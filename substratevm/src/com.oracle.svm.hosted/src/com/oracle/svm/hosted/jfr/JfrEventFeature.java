/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jfr;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability.ValueAvailability;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.JfrJavaEvents;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdMap;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.event.Event;
import jdk.jfr.internal.JVM;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Support for Java-level JFR events. This feature is only present if the {@link JfrFeature} is used
 * as well but it needs functionality that is only available in com.oracle.svm.hosted.
 */
@AutomaticallyRegisteredFeature
public class JfrEventFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JfrFeature.isInConfiguration(false);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(JfrFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        for (Class<?> eventSubClass : config.findSubclasses(Event.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(eventSubClass.getName());
        }
        config.registerSubstitutionProcessor(new JfrEventSubstitution(metaAccess));
    }

    // PR-313 investigation: toggle to compare Class-hop (disabled) vs direct path (enabled).
    private static final boolean ENABLE_JFR_FIELD_VALUE_TRANSFORMER = Boolean.parseBoolean(
                    System.getProperty("jfr.fieldValueTransformer", "false"));

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ENABLE_JFR_FIELD_VALUE_TRANSFORMER) {
            return;
        }
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(DynamicHubCompanion.class, "jfrEventConfiguration"), new FieldValueTransformerWithAvailability() {
            @Override
            public ValueAvailability valueAvailability() {
                return ValueAvailability.AfterAnalysis;
            }

            @Override
            public Object transform(Object receiver, Object originalValue) {
                return originalValue;
            }
        });
    }

    @Override
    public void beforeImageWrite(Feature.BeforeImageWriteAccess access) {
        if (!com.oracle.svm.core.SubstrateOptions.Name.getValue().contains("llvmvm")) {
            return;
        }
        FeatureImpl.BeforeImageWriteAccessImpl impl = (FeatureImpl.BeforeImageWriteAccessImpl) access;
        HostedMetaAccess meta = impl.getHostedMetaAccess();
        NativeImageHeap heap = impl.getImage().getHeap();

        DynamicHub hub = meta.lookupJavaType(com.oracle.svm.core.jfr.events.EveryChunkNativePeriodicEvents.class).getHub();
        Object hostedConfig = hub.getJfrEventConfiguration();

        JavaConstant hubConstant = com.oracle.svm.core.meta.SubstrateObjectConstant.forObject(hub);
        NativeImageHeap.ObjectInfo hubInfo = heap.getConstantInfo(hubConstant);

        HostedField companionField = (HostedField) meta.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "companion"));
        JavaConstant companionConstant = heap.hConstantReflection.readFieldValue(companionField, hubInfo.getConstant());

        HostedField jfrField = (HostedField) meta.lookupJavaField(ReflectionUtil.lookupField(DynamicHubCompanion.class, "jfrEventConfiguration"));
        JavaConstant heapReadConfig = heap.hConstantReflection.readFieldValue(jfrField, companionConstant);

        JavaConstant shadowConfig = null;
        if (companionConstant instanceof com.oracle.graal.pointsto.heap.ImageHeapInstance ihi) {
            shadowConfig = ihi.readFieldValue((com.oracle.graal.pointsto.meta.AnalysisField) jfrField.getWrapped());
        }

        System.err.println("DEBUG JFR step4b image=" + com.oracle.svm.core.SubstrateOptions.Name.getValue() +
                        " hubId=" + JfrDebugProbe.everyChunkHubId +
                        " companionId=" + JfrDebugProbe.everyChunkCompanionId +
                        " buildConfigId=" + JfrDebugProbe.everyChunkConfigId +
                        " hostedConfigNonNull=" + (hostedConfig != null) +
                        " hostedConfigId=" + (hostedConfig == null ? 0 : System.identityHashCode(hostedConfig)) +
                        " heapReadConfigNonNull=" + (heapReadConfig != null && !heapReadConfig.isNull()) +
                        " heapReadConfigId=" + (heapReadConfig == null || heapReadConfig.isNull() ? 0
                                        : System.identityHashCode(heap.hUniverse.getSnippetReflection().asObject(Object.class, heapReadConfig))) +
                        " shadowConfigNonNull=" + (shadowConfig != null && !shadowConfig.isNull()) +
                        " hubConstantKind=" + hubInfo.getConstant().getClass().getSimpleName() +
                        " companionConstantKind=" + companionConstant.getClass().getSimpleName() +
                        " hubInclusionReason=" + hubInfo);

        NativeImageHeap.ObjectInfo companionInfo = companionConstant.isNull() ? null : heap.getConstantInfo(companionConstant);
        System.err.println("DEBUG JFR step4g-build image=" + com.oracle.svm.core.SubstrateOptions.Name.getValue() +
                        " hubHeapAddress=" + hubInfo.getAddress() +
                        " companionHeapAddress=" + (companionInfo == null ? 0 : companionInfo.getAddress()) +
                        " hubPointsToCompanionAddress=" + JfrDebugProbe.everyChunkHubPointsToCompanionAddress +
                        " configHeapAddress=" + JfrDebugProbe.everyChunkConfigHeapAddress +
                        " jfrFieldBufferIndex=" + JfrDebugProbe.everyChunkJfrFieldBufferIndex +
                        " jfrFieldOffset=" + JfrDebugProbe.everyChunkJfrFieldOffset +
                        " partition=" + JfrDebugProbe.everyChunkPartitionName +
                        " assignImmutable=" + JfrDebugProbe.everyChunkAssignImmutable +
                        " rawAfterFieldWrite=" + JfrDebugProbe.everyChunkJfrFieldRawAfterWrite +
                        " rawAfterFullHeapWrite=" + JfrDebugProbe.everyChunkJfrFieldRawAfterFullHeapWrite +
                        " hubCompanionAddrsMatch=" + (companionInfo != null && companionInfo.getAddress() == JfrDebugProbe.everyChunkHubPointsToCompanionAddress));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        // Reserve slot 0 for error-catcher.
        int mapSize = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId() + 1;

        // Create trace-ID map with fixed size.
        ImageSingletons.lookup(JfrTraceIdMap.class).initialize(mapSize);

        // Scan all classes and build sets of packages, modules and class-loaders. Count all items.
        Collection<? extends SharedType> types = ((FeatureImpl.CompilationAccessImpl) a).getTypes();
        for (SharedType type : types) {
            DynamicHub hub = type.getHub();
            Class<?> clazz = hub.getHostedJavaClass();
            // Off-set by one for error-catcher
            JfrTraceId.assign(clazz, hub.getTypeID() + 1);
        }

        /* Store the event configuration in the dynamic hub companion. */
        try {
            FeatureImpl.CompilationAccessImpl accessImpl = ((FeatureImpl.CompilationAccessImpl) a);
            Method getConfiguration = JVM.class.getDeclaredMethod("getConfiguration", Class.class);
            for (var newEventClass : JfrJavaEvents.getAllEventClasses()) {
                Object ec = getConfiguration.invoke(JVM.getJVM(), newEventClass);
                DynamicHub dynamicHub = accessImpl.getMetaAccess().lookupJavaType(newEventClass).getHub();
                dynamicHub.setJrfEventConfiguration(ec);
                if (newEventClass == com.oracle.svm.core.jfr.events.EveryChunkNativePeriodicEvents.class) {
                    JfrDebugProbe.recordEveryChunk(dynamicHub, ec);
                    System.err.println("DEBUG JFR step2b image=" + com.oracle.svm.core.SubstrateOptions.Name.getValue() +
                                    " hubId=" + JfrDebugProbe.everyChunkHubId +
                                    " companionId=" + JfrDebugProbe.everyChunkCompanionId +
                                    " configId=" + JfrDebugProbe.everyChunkConfigId);
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
