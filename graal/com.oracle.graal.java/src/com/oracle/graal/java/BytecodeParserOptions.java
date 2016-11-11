/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.java;

import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionKey;
import com.oracle.graal.options.StableOptionKey;

/**
 * Options related to {@link BytecodeParser}.
 *
 * Note: This must be a top level class to work around for
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
public class BytecodeParserOptions {
    // @formatter:off
    @Option(help = "The trace level for the bytecode parser used when building a graph from bytecode", type = OptionType.Debug)
    public static final OptionKey<Integer> TraceBytecodeParserLevel = new OptionKey<>(0);

    @Option(help = "Inlines trivial methods during bytecode parsing.", type = OptionType.Expert)
    public static final StableOptionKey<Boolean> InlineDuringParsing = new StableOptionKey<>(true);

    @Option(help = "Inlines intrinsic methods during bytecode parsing.", type = OptionType.Expert)
    public static final StableOptionKey<Boolean> InlineIntrinsicsDuringParsing = new StableOptionKey<>(true);

    @Option(help = "Traces inlining performed during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionKey<Boolean> TraceInlineDuringParsing = new StableOptionKey<>(false);

    @Option(help = "Traces use of plugins during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionKey<Boolean> TraceParserPlugins = new StableOptionKey<>(false);

    @Option(help = "Maximum depth when inlining during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionKey<Integer> InlineDuringParsingMaxDepth = new StableOptionKey<>(10);

    @Option(help = "Dump graphs after non-trivial changes during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionKey<Boolean> DumpDuringGraphBuilding = new StableOptionKey<>(false);

    @Option(help = "When creating info points hide the methods of the substitutions.", type = OptionType.Debug)
    public static final OptionKey<Boolean> HideSubstitutionStates = new OptionKey<>(false);

    @Option(help = "Use intrinsics guarded by a virtual dispatch test at indirect call sites.", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseGuardedIntrinsics = new OptionKey<>(true);
    // @formatter:on
}
