/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.model;

import org.ballerinalang.model.symbols.BLangSymbol;

/**
 * Represents a construct that have a predefined structure in ballerina.
 * Known Implementations are:
 * <ul>
 * <li>{@code StructDef}</li>
 * <li>{@code AnnotationDef}</li>
 * </ul>
 * 
 * @since 0.8.5
 */
public interface StructuredUnit {
    
    /**
     * Resolve a symbol name in the current scope only.
     * 
     * @param name SymbolName to be resolved
     * @return Symbol with having the given symbol name in the current scope.
     */
    public BLangSymbol resolveMembers(SymbolName name);
}
