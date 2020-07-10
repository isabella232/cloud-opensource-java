/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.classpath;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A linkage error.
 *
 * @see <a href="https://jlbp.dev/glossary.html#linkage-error">Java Dependency Glossary: Linkage
 *     Error</a>
 */
public abstract class LinkageProblem {

  private final Symbol symbol;
  private final ClassFile sourceClass;
  private final String symbolProblemMessageFormat;

  LinkageProblem(String symbolProblemMessage, ClassFile sourceClass, Symbol symbol) {
    this.symbolProblemMessageFormat = Preconditions.checkNotNull(symbolProblemMessage);
    Preconditions.checkNotNull(symbol);

    // After finding symbol problem, there is no need to have SuperClassSymbol over ClassSymbol.
    this.symbol =
        symbol instanceof SuperClassSymbol ? new ClassSymbol(symbol.getClassBinaryName()) : symbol;
    this.sourceClass = Preconditions.checkNotNull(sourceClass);
  }

  /** Returns the target symbol that was not resolved. */
  public Symbol getSymbol() {
    return symbol;
  }

  /** Returns the source of the invalid reference which this linkage error represents. */
  public ClassFile getSourceClass() {
    return sourceClass;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    LinkageProblem that = (LinkageProblem) other;
    return symbol.equals(that.symbol) && Objects.equals(sourceClass, that.sourceClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbol, sourceClass);
  }

  @Override
  public final String toString() {
    return formatSymbolProblem() + " referenced by " + sourceClass;
  }

  /**
   * Returns the description of the problem on the {@code symbol}. This description does not include
   * the {@code sourceClass}. This value is useful when grouping {@link LinkageProblem}s by their
   * {@code symbol}s.
   */
  public String formatSymbolProblem() {
    return String.format(symbolProblemMessageFormat, symbol.toString());
  }

  /** Returns mapping from symbol problem description to the names of the source classes. */
  public static ImmutableMap<String, ImmutableList<String>> groupBySymbolProblem(
      Iterable<LinkageProblem> linkageProblems) {
    ImmutableListMultimap<String, LinkageProblem> groupedMultimap =
        Multimaps.index(linkageProblems, problem -> problem.formatSymbolProblem());

    ListMultimap<String, String> symbolProblemToSourceClasses =
        Multimaps.transformValues(
            groupedMultimap, problem -> problem.getSourceClass().getBinaryName());
    Map<String, ImmutableList<String>> valueTransformed =
        Maps.transformValues(symbolProblemToSourceClasses.asMap(), ImmutableList::copyOf);
    return ImmutableMap.copyOf(valueTransformed);
  }

  /** Returns the formatted {@code linkageProblems} by grouping them by the {@code symbol}s. */
  public static String formatLinkageProblems(Set<LinkageProblem> linkageProblems) {
    StringBuilder output = new StringBuilder();

    // Group by the symbols
    ImmutableListMultimap<Symbol, LinkageProblem> groupBySymbols =
        Multimaps.index(linkageProblems, problem -> problem.getSymbol());

    groupBySymbols
        .asMap()
        .forEach(
            (symbol, problems) -> {
              // problems all have the same symbol problem
              LinkageProblem firstProblem = Iterables.getFirst(problems, null);
              int referenceCount = problems.size();
              output.append(
                  String.format(
                      "%s;\n  referenced by %d class file%s\n",
                      firstProblem.formatSymbolProblem(),
                      referenceCount,
                      referenceCount > 1 ? "s" : ""));
              problems.forEach(
                  problem -> {
                    ClassFile sourceClassFile = problem.getSourceClass();
                    output.append("    " + sourceClassFile.getBinaryName());
                    output.append(" (" + sourceClassFile.getClassPathEntry() + ")\n");
                  });
            });

    return output.toString();
  }
}
