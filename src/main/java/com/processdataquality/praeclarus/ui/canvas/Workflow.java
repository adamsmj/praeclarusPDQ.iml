/*
 * Copyright (c) 2021 Queensland University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.processdataquality.praeclarus.ui.canvas;

import com.processdataquality.praeclarus.logging.Logger;
import com.processdataquality.praeclarus.node.Network;
import com.processdataquality.praeclarus.node.Node;
import com.processdataquality.praeclarus.node.NodeStateListener;
import com.processdataquality.praeclarus.plugin.Option;
import com.processdataquality.praeclarus.plugin.Options;
import com.processdataquality.praeclarus.ui.component.VertexLabelDialog;
import com.processdataquality.praeclarus.ui.component.WorkflowPanel;
import com.vaadin.flow.component.notification.Notification;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Michael Adams
 * @date 19/5/21
 */
public class Workflow implements CanvasEventListener, NodeStateListener {

    private enum State { VERTEX_DRAG, ARC_DRAW, NONE }

    private final WorkflowPanel _container;
    private final Context2D _ctx;
    private final Set<Vertex> _vertices = new HashSet<>();
    private final Set<Connector> _connectors = new HashSet<>();
    private final Set<CanvasSelectionListener> _selectionListeners = new HashSet<>();

    private Network _network;        // back end
    private ActiveLine activeLine;
    private CanvasPrimitive selected;
    private State state = State.NONE;
    private boolean _loading = false;
    private boolean _changed = false;


    public Workflow(WorkflowPanel container, Context2D context) {
        _container = container;
        _ctx = context;
        clear();
    }

    @Override
    public void mouseDown(double x, double y) {
        Port port = getPortAt(x, y);
        if (port != null && port.isOutput()) {
            activeLine = new ActiveLine(x, y);
            state = State.ARC_DRAW;
        }
        else {
            Vertex vertex = getVertexAt(x, y);
            if (vertex != null) {
                setSelected(vertex);
                vertex.setDragOffset(x, y);
                state = State.VERTEX_DRAG;
            }
        }
    }

    @Override
    public void mouseMove(double x, double y) {
        if (state == State.NONE) return;

        if (state == State.ARC_DRAW) {
            render();
            activeLine.lineTo(_ctx, x, y);
        }
        else if (state == State.VERTEX_DRAG) {
            ((Vertex) selected).moveTo(x, y);
            setChanged(true);
            render();
        }
    }

    @Override
    public void mouseUp(double x, double y) {
        if (state == State.NONE) return;

        if (state == State.ARC_DRAW) {
            Point start = activeLine.getStart();
            Port source = getPortAt(start.x, start.y);
            Port target = getPortAt(x, y);
            if (source != null && target != null) {
                if (source.isOutput() && target.isInput()) {
                    addConnector(new Connector(source, target));
                }
                else {
                    Notification.show("Output port cannot be the target of a connection");
                }
            }
        }

        state = State.NONE;     // if dragging nothing more to do
        render();
    }

    @Override
    public void mouseClick(double x, double y) {
        setSelected(x, y);
        _container.changedSelected(getSelectedNode());
        render();
    }

    @Override
    public void mouseDblClick(double x, double y) {
        mouseClick(x, y);
        Node node = getSelectedNode();
        if (node != null) {
            new VertexLabelDialog(this, node).open();
        }
    }

    @Override
    public void fileLoaded(String jsonStr) {
        WorkflowLoader loader = new WorkflowLoader(this);
        try {
            loader.load(jsonStr);
            _container.getRunner().reset();
            Logger.workflowLoadEvent("user", "dummy");
        }
        catch (JSONException | IOException je) {
            Notification.show("Failed to load file: " + je.getMessage());
            clear();
        }
    }


    @Override
    public void nodeStateChanged(Node node) throws Exception {
        switch (node.getState()) {
            case UNSTARTED:
                changeStateIndicator(node, VertexStateIndicator.State.DORMANT);
                break;
            case EXECUTING:
                changeStateIndicator(node, VertexStateIndicator.State.RUNNING);
                break;
            case PAUSED:
                changeStateIndicator(node, VertexStateIndicator.State.PAUSED);
                break;
            case COMPLETED:
                changeStateIndicator(node, VertexStateIndicator.State.COMPLETED);
                break;
        }
    }


    public void addVertexSelectionListener(CanvasSelectionListener listener) {
        _selectionListeners.add(listener);
    }


    public boolean removeVertexSelectionListener(CanvasSelectionListener listener) {
        return _selectionListeners.remove(listener);
    }


    public void setChanged(boolean b) {
        if (_changed != b) {
            _changed = b;
            _container.setWorkflowChanged(b);
        }
    }

    public boolean hasChanges() { return _changed; }


    public void clear(Network network) {
        _vertices.clear();
        _connectors.clear();
        _network = network;
        setChanged(false);
        render();
    }

    private void clear() {
        Network network = new Network.Builder("user").name("New Workflow").build();
        clear(network);
        Logger.saveNetwork(network);
    }


    public void setLoading(boolean b) {
        _loading = b;
        if (! _loading) render();
    }


    public Network getNetwork() { return _network; }


    public boolean hasContent() {
        return ! _vertices.isEmpty();
    }


    public CanvasPrimitive getSelected() { return selected; }

    public void setSelected(CanvasPrimitive primitive) {
        selected = primitive;
        render();
        announceSelectionChange();
        _container.canvasSelectionChanged(selected);
    }

    public Node getSelectedNode() {
        Vertex vertex = getSelectedVertex();
        return vertex != null ? vertex.getNode() : null;
    }


    public Vertex getSelectedVertex() {
        return selected instanceof Vertex ? ((Vertex) selected) : null;
    }


    public void setSelectedNode(Node node) {
        for (Vertex vertex : _vertices) {
            if (vertex.getNode().equals(node)) {
                setSelected(vertex);
                _container.changedSelected(node);
                break;
            }
        }
    }


     public void removeSelected() {
        if (selected instanceof Vertex) {
            removeVertex((Vertex) selected);
            setSelected(null);
        }
        else if (selected instanceof Connector) {
            removeConnector((Connector) selected);
            setSelected(null);
        }
    }

    
    public void addVertex(Vertex vertex) {
        _vertices.add(vertex);
        _network.addNode(vertex.getNode());
        setSelected(vertex);
        vertex.getNode().addStateListener(this);
        setChanged(true);
    }


    public void addVertex(Node node) {
        Point p = getSuitableInsertPoint();
        addVertex(new Vertex(p.x, p.y, node));
    }


    public void removeVertex(Vertex vertex) {
        if (vertex != null) {
            _vertices.remove(vertex);
            removeConnectors(vertex);
            vertex.getNode().removeStateListener(this);
            _network.removeNode(vertex.getNode());
            setChanged(true);
        }
    }


    public void addConnector(Connector c) {
        _connectors.add(c);
        Node source = c.getSource().getNode();
        Node target = c.getTarget().getNode();
        source.connect(target);
        setChanged(true);
        render();
    }


    public boolean removeConnector(Connector c) {
        boolean success = _connectors.remove(c);
        if (success) {
            Node source = c.getSource().getNode();
            Node target = c.getTarget().getNode();
            source.disconnect(target);
            setChanged(true);
            render();
        }
        return success;
    }


    protected void setName(String name) { _network.updateName(name); }


    public JSONObject asJson() throws JSONException {
        JSONArray vertexArray = new JSONArray();
        for (Vertex vertex : _vertices) {
            vertexArray.put(vertex.asJson());
        }
        JSONArray connectorArray = new JSONArray();
        for (Connector connector : _connectors) {
            connectorArray.put(connector.asJson());
        }
        JSONObject json = _network.asJson();
        json.put("vertices", vertexArray);
        json.put("connectors", connectorArray);
        return json;
    }


    public Options getUserOptions() {
        Options options = new Options();
        options.addDefault("Workflow Name", _network.getName());
        return options;
    }


    public void setUserOption(Option option) {
        if (option.key().equals("Workflow Name")) {
            String newName = option.asString();
            if (! newName.isEmpty()) {
                setName(newName);
            }
        }
    }
    

    private void removeConnectors(Vertex vertex) {
        Set<Connector> removeSet = new HashSet<>();
        for (Connector c : _connectors) {
            if (c.connects(vertex)) {
                removeSet.add(c);
            }
        }
        for (Connector c : removeSet) {
            removeConnector(c);
        }
        render();
    }


    private Port getPortAt(double x, double y) {
        for (Vertex vertex : _vertices) {
            Port port = vertex.getPortAt(x, y);
             if (port != null) {
                 return port;
             }
        }
        return null;
    }

    private Vertex getVertexAt(double x, double y) {
        for (Vertex vertex : _vertices) {
            if (vertex.contains(x, y)) {
                return vertex;
            }
        }
        return null;
    }


    public void render() {
        if (_loading) return;                 // don't render while loading from file
        _ctx.clear();
        for (Vertex vertex : _vertices) {
            vertex.render(_ctx, selected);
        }
        for (Connector connector : _connectors) {
            connector.render(_ctx, selected);
        }
    }


    private void setSelected(double x, double y) {
        for (Vertex vertex : _vertices) {
            if (vertex.contains(x, y)) {
                setSelected(vertex);
                return;
            }
        }
        for (Connector connector : _connectors) {
            if (connector.contains(x, y)) {
                setSelected(connector);
                return;
            }
        }
        setSelected(null);
    }


    private void announceSelectionChange() {
        for (CanvasSelectionListener listener : _selectionListeners) {
            listener.canvasSelectionChanged(getSelected());
        }
    }


    private void changeStateIndicator(Node node, VertexStateIndicator.State state) {
        setSelectedNode(node);
        getSelectedVertex().setRunState(state);
        render();
    }


    private Point getSuitableInsertPoint() {
        double x = 50;
        double sepSpace = 100;
        double y = 50;
        boolean overlap;
        do {
            overlap = false;
            for (Vertex vertex : _vertices) {
                if (! vertex.contains(x + 10, y + 10)) continue;

                overlap = true;
                x = x + Vertex.WIDTH + sepSpace;
            }
        } while (overlap);

        return new Point(x, y);
    }

}
