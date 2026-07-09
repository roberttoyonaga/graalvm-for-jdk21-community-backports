/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */
package com.oracle.svm.hosted.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;

/**
 * Temporary probe state for PR #313 JFR investigation. Remove before merge.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class JfrDebugProbe {
    public static volatile int everyChunkHubId;
    public static volatile int everyChunkCompanionId;
    public static volatile int everyChunkConfigId;

    public static volatile long everyChunkHubHeapAddress;
    public static volatile long everyChunkCompanionHeapAddress;
    public static volatile long everyChunkConfigHeapAddress;
    public static volatile long everyChunkHubPointsToCompanionAddress;
    public static volatile int everyChunkJfrFieldBufferIndex;
    public static volatile long everyChunkJfrFieldRawAfterWrite;
    public static volatile long everyChunkJfrFieldRawAfterFullHeapWrite;
    public static volatile int everyChunkJfrFieldOffset;
    public static volatile boolean everyChunkAssignImmutable;
    public static volatile String everyChunkPartitionName;

    private JfrDebugProbe() {
    }

    public static void recordEveryChunk(DynamicHub hub, Object config) {
        everyChunkHubId = System.identityHashCode(hub);
        everyChunkCompanionId = System.identityHashCode(hub.getCompanion());
        everyChunkConfigId = config == null ? 0 : System.identityHashCode(config);
    }

    public static boolean isEveryChunkCompanion(Object companion) {
        return everyChunkCompanionId != 0 && System.identityHashCode(companion) == everyChunkCompanionId;
    }

    public static boolean isEveryChunkHub(DynamicHub hub) {
        return everyChunkHubId != 0 && System.identityHashCode(hub) == everyChunkHubId;
    }
}
