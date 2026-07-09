/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */
package com.oracle.svm.hosted.jfr;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Temporary compile-time probes for PR #313 constant-fold investigation. Remove before merge.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class JfrCompileGraphProbe {
    private JfrCompileGraphProbe() {
    }

    private static boolean enabled() {
        return SubstrateOptions.Name.getValue().contains("llvmvm");
    }

    private static String buildPhase() {
        if (BuildPhaseProvider.isCompilationFinished()) {
            return "afterCompilation";
        }
        if (BuildPhaseProvider.isReadyForCompilation()) {
            return "duringCompilation";
        }
        if (BuildPhaseProvider.isAnalysisFinished()) {
            return "afterAnalysis";
        }
        return "duringAnalysis";
    }

    public static void logConstantFold(ResolvedJavaMethod method, ResolvedJavaField field, JavaConstant receiver, ConstantNode folded) {
        if (!enabled() || !isJfrEventConfigurationField(field)) {
            return;
        }
        String receiverDesc = receiver == null ? "static" : (receiver.isNull() ? "null" : receiver.getClass().getSimpleName());
        System.err.println("DEBUG JFR step5-fold image=" + SubstrateOptions.Name.getValue() +
                        " phase=" + buildPhase() +
                        " method=" + formatMethod(method) +
                        " field=" + field.getDeclaringClass().getName() + "." + field.getName() +
                        " receiver=" + receiverDesc +
                        " foldedNull=" + (folded.asJavaConstant().isDefaultForKind()));
    }

    public static void inspectGraphBeforeEncode(HostedMethod method, StructuredGraph graph) {
        if (!enabled() || !isPeriodicEventSetup(method)) {
            return;
        }
        int jfrLoadFieldCount = 0;
        int jfrLoadFieldConstReceiver = 0;
        int objectNullConstantCount = 0;
        int objectNonNullConstantCount = 0;
        int getJfrConfigInvokeCount = 0;
        StringBuilder loadDetails = new StringBuilder();
        for (Node node : graph.getNodes()) {
            if (node instanceof LoadFieldNode loadField && isJfrEventConfigurationField(loadField.field())) {
                jfrLoadFieldCount++;
                boolean constReceiver = loadField.object() != null && loadField.object().isConstant();
                if (constReceiver) {
                    jfrLoadFieldConstReceiver++;
                }
                if (loadDetails.length() > 0) {
                    loadDetails.append(';');
                }
                loadDetails.append("constReceiver=").append(constReceiver);
            } else if (node instanceof ConstantNode constant && constant.isJavaConstant()) {
                JavaConstant jc = constant.asJavaConstant();
                if (jc.getJavaKind().isObject()) {
                    if (jc.isNull()) {
                        objectNullConstantCount++;
                    } else {
                        objectNonNullConstantCount++;
                    }
                }
            } else if (node instanceof InvokeNode invoke && isGetJfrEventConfiguration(invoke.getTargetMethod())) {
                getJfrConfigInvokeCount++;
            } else if (node instanceof InvokeWithExceptionNode invoke && isGetJfrEventConfiguration(invoke.getTargetMethod())) {
                getJfrConfigInvokeCount++;
            }
        }
        System.err.println("DEBUG JFR step5-graph image=" + SubstrateOptions.Name.getValue() +
                        " phase=" + buildPhase() +
                        " method=" + formatMethod(method) +
                        " jfrLoadFieldCount=" + jfrLoadFieldCount +
                        " jfrLoadFieldConstReceiver=" + jfrLoadFieldConstReceiver +
                        " objectNullConstantCount=" + objectNullConstantCount +
                        " objectNonNullConstantCount=" + objectNonNullConstantCount +
                        " getJfrConfigInvokeCount=" + getJfrConfigInvokeCount +
                        " loadDetails=" + loadDetails);
    }

    public static void inspectGetJfrEventConfigurationGraph(HostedMethod method, StructuredGraph graph) {
        if (!enabled() || !isGetJfrEventConfiguration(method)) {
            return;
        }
        int jfrLoadFieldCount = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof LoadFieldNode loadField && isJfrEventConfigurationField(loadField.field())) {
                jfrLoadFieldCount++;
            }
        }
        System.err.println("DEBUG JFR step5-getter image=" + SubstrateOptions.Name.getValue() +
                        " phase=" + buildPhase() +
                        " method=" + formatMethod(method) +
                        " jfrLoadFieldCount=" + jfrLoadFieldCount);
    }

    private static boolean isJfrEventConfigurationField(ResolvedJavaField field) {
        return "jfrEventConfiguration".equals(field.getName()) &&
                        DynamicHubCompanion.class.getName().equals(field.getDeclaringClass().getName());
    }

    private static boolean isPeriodicEventSetup(HostedMethod method) {
        return method.getName().equals("periodicEventSetup") &&
                        method.format("%H").contains("JfrManager");
    }

    private static boolean isGetJfrEventConfiguration(ResolvedJavaMethod target) {
        return target != null && "getJfrEventConfiguration".equals(target.getName());
    }

    private static boolean isGetJfrEventConfiguration(HostedMethod method) {
        return method.getName().equals("getJfrEventConfiguration") &&
                        method.format("%H").contains("DynamicHub");
    }

    private static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n(%P)");
    }
}
