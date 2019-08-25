/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tamaya.resolver.internal;

import org.apache.tamaya.resolver.spi.ExpressionEvaluator;
import org.apache.tamaya.resolver.spi.ExpressionResolver;
import org.apache.tamaya.spi.PropertyValue;
import org.apache.tamaya.spi.ServiceContextManager;

import javax.annotation.Priority;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default expression evaluator that manages several instances of {@link org.apache.tamaya.resolver.spi.ExpressionResolver}.
 * Each resolver is identified by a resolver id. Each expression passed has the form resolverId:resolverExpression, which
 * has the advantage that different resolvers can be active in parallel.
 */
@Priority(10000)
public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    private static final Logger LOG = Logger.getLogger(DefaultExpressionEvaluator.class.getName());

    List<ExpressionResolver> resolvers = new ArrayList<>();

    /**
     * Default constructor loading its resolvers from the current service context.
     */
    public DefaultExpressionEvaluator(){
        loadResolversFromServiceContext();
    }

    /**
     * Default constructor loading its resolvers from the current service context.
     * @param resolvers the resolvers to be used for evaluation, resolvers at the beginning have precedence.
     */
    public DefaultExpressionEvaluator(List<ExpressionResolver> resolvers){
        this.resolvers.addAll(resolvers);
    }

    /**
     * Comparator used (not needed with Java8).
     */
    private static final Comparator<ExpressionResolver> RESOLVER_COMPARATOR = new Comparator<ExpressionResolver>() {
        @Override
        public int compare(ExpressionResolver o1, ExpressionResolver o2) {
            return compareExpressionResolver(o1, o2);
        }
    };

    /**
     * Order ExpressionResolver reversely, the most important come first.
     *
     * @param res1 the first ExpressionResolver
     * @param res2 the second ExpressionResolver
     * @return the comparison result.
     */
    private static int compareExpressionResolver(ExpressionResolver res1, ExpressionResolver res2) {
        Priority prio1 = res1.getClass().getAnnotation(Priority.class);
        Priority prio2 = res2.getClass().getAnnotation(Priority.class);
        int ord1 = prio1 != null ? prio1.value() : 0;
        int ord2 = prio2 != null ? prio2.value() : 0;
        if (ord1 < ord2) {
            return -1;
        } else if (ord1 > ord2) {
            return 1;
        } else {
            return res1.getClass().getName().compareTo(res2.getClass().getName());
        }
    }

    @Override
    public PropertyValue evaluateExpression(PropertyValue propertyValue, boolean maskUnresolved) {
        if(propertyValue==null || propertyValue.getValue()==null){
            return null;
        }
        String value = propertyValue.getValue();
        StringTokenizer tokenizer = new StringTokenizer(value, "${}", true);
        StringBuilder resolvedValue = new StringBuilder();
        StringBuilder current = new StringBuilder();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            switch (token) {
                case "$":
                    String nextToken = tokenizer.hasMoreTokens()?tokenizer.nextToken():"";
                    if (!"{".equals(nextToken)) {
                        current.append(token);
                        current.append(nextToken);
                        break;
                    }
                    if(value.indexOf('}')<=0){
                        current.append(token);
                        current.append(nextToken);
                        break;
                    }
                    String subExpression = parseSubExpression(tokenizer, value);
                    String res = evaluateInternal(propertyValue, subExpression, maskUnresolved);
                    if(res!=null) {
                        current.append(res);
                    }
                    break;
                default:
                    current.append(token);
            }
        }
        if (current.length() > 0) {
            resolvedValue.append(current);
        }
        return propertyValue.setValue(resolvedValue.toString());
    }

    @Override
    public Collection<ExpressionResolver> getResolvers() {
        return resolvers;
    }

    private void loadResolversFromServiceContext() {
        resolvers.addAll(ServiceContextManager.getServiceContext().getServices(ExpressionResolver.class));
        resolvers.sort(RESOLVER_COMPARATOR);
    }

    /**
     * Parses subexpression from tokenizer, hereby counting all open and closed brackets, but ignoring any
     * getMeta characters.
     * @param tokenizer the current tokenizer instance
     * @param valueToBeFiltered subexpression to be filtered for
     * @return the parsed sub expression
     */
    private String parseSubExpression(StringTokenizer tokenizer, String valueToBeFiltered) {
        StringBuilder expression = new StringBuilder();
        boolean escaped = false;
        while(tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            switch (token) {
                case "\\":
                    if(!escaped) {
                        escaped = true;

                    } else {
                        expression.append(token);
                        escaped = false;
                    }
                    break;
                case "{":
                    if(!escaped) {
                        LOG.warning("Ignoring not escaped '{' in : " + valueToBeFiltered);
                    }
                    expression.append(token);
                    escaped = false;
                    break;
                case "$":
                    if(!escaped) {
                        LOG.warning("Ignoring not escaped '$' in : " + valueToBeFiltered);
                    }
                    expression.append(token);
                    escaped = false;
                    break;
                case "}":
                    if(escaped) {
                        expression.append(token);
                        escaped = false;
                    } else{
                        return expression.toString();
                    }
                    break;
                default:
                    expression.append(token);
                    escaped = false;
                    break;
            }
        }
        LOG.warning("Invalid expression syntax in: " + valueToBeFiltered + ", expression does not close!");
            return valueToBeFiltered;
    }

    /**
     * Evaluates the expression parsed, hereby checking for prefixes and trying otherwise all available resolvers,
     * based on priority.
     *
     * @param propertyValue the current value, not null.
     * @param unresolvedExpression the parsed, but unresolved expression
     * @param maskUnresolved if true, not found expression parts will be replaced by surrounding with [].
     *                     Setting to false will replace the createValue with an empty String.
     * @return the resolved expression, or null.
     */
    private String evaluateInternal(PropertyValue propertyValue, String unresolvedExpression, boolean maskUnresolved) {
        String value = null;
        // 1 check for explicit prefix
        String resolverRefs = propertyValue.getMeta("resolvers");
        if(resolverRefs==null){
            resolverRefs = "";
        }
        Collection<ExpressionResolver> resolvers = getResolvers();
        for(ExpressionResolver resolver:resolvers){
            if(unresolvedExpression.startsWith(resolver.getResolverPrefix())){
                value = resolver.evaluate(unresolvedExpression.substring(resolver.getResolverPrefix().length()));
                if(value!=null){
                    resolverRefs += resolver.getClass().getName() + ", ";
                    propertyValue.setMeta("resolvers", resolverRefs);
                }
                break;
            }
        }
        // Lookup system and environment props as defaults...
        if(value==null){
            value = System.getProperty(unresolvedExpression);
            if(value!=null){
                resolverRefs += "system-property, ";
                propertyValue.setMeta("resolvers", resolverRefs);
            }
        }
        if(value==null){
            value = System.getenv(unresolvedExpression);
            if(value!=null){
                resolverRefs += "environment-property, ";
                propertyValue.setMeta("resolvers", resolverRefs);
            }
        }
        if(value==null){
            LOG.log(Level.WARNING, "Unresolvable expression encountered " + unresolvedExpression);
            if(maskUnresolved){
                value = "?{" + unresolvedExpression + '}';
                resolverRefs += "<unresolved>, ";
                propertyValue.setMeta("resolvers", resolverRefs);
            }
        }
        return value;
    }


}
