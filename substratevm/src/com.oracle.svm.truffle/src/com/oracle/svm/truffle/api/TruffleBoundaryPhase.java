/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.truffle.api;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase.SubstrateBytecodeParser;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Deoptimize for {@link TruffleBoundary} calls when {@link TruffleBoundary#transferToInterpreter()}
 * is true. Note that most of the deoptimizations are already inserted during parsing in
 * {@link SubstrateBytecodeParser}. However, during parsing we cannot optimize virtual call sites
 * that get canonicalized to static calls only during partial evaluation. So we re-check all invokes
 * that remain after partial evaluation here.
 */
public class TruffleBoundaryPhase extends Phase {

    @Override
    @SuppressWarnings("deprecation")
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invoke = (InvokeWithExceptionNode) n;
                ExceptionObjectNode exceptionObject = (ExceptionObjectNode) invoke.exceptionEdge();

                FixedNode originalNext = exceptionObject.next();
                if (!(originalNext instanceof DeoptimizeNode) && invoke.callTarget().targetMethod() != null) {
                    TruffleBoundary truffleBoundary = invoke.callTarget().targetMethod().getAnnotation(TruffleBoundary.class);
                    if (truffleBoundary != null) {
                        if (!truffleBoundary.throwsControlFlowException() && truffleBoundary.transferToInterpreterOnException()) {
                            addDeoptimizeNode(graph, originalNext);
                        }
                    }
                }
            }
        }
    }

    private static void addDeoptimizeNode(StructuredGraph graph, FixedNode originalNext) {
        DeoptimizeNode deoptimize = graph.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.NotCompiledExceptionHandler));
        originalNext.replaceAtPredecessor(deoptimize);
        GraphUtil.killCFG(originalNext);
    }
}
