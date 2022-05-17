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

package com.processdataquality.praeclarus.logging.entity;

import com.processdataquality.praeclarus.logging.LogConstant;
import com.processdataquality.praeclarus.node.Graph;

import javax.persistence.Entity;

/**
 * @author Michael Adams
 * @date 30/11/21
 */
@Entity
public class GraphIOEvent extends AbstractGraphEvent {


    protected GraphIOEvent() { }
    
    public GraphIOEvent(Graph graph, String user, LogConstant label) {
        super(graph, user, label);
    }

    public GraphIOEvent(String id, String name, String user, LogConstant label) {
        super(id, name, user, label);
    }


    @Override
    public String toString() {
        return super.toString() ;
    }

}