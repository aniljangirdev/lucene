/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.codecs.blocktreeords;

import java.io.IOException;
import org.apache.lucene.codecs.blocktreeords.FSTOrdsOutputs.Output;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.fst.FST;

/** BlockTree's implementation of {@link Terms}. */
final class OrdsFieldReader extends Terms {
  final long numTerms;
  final FieldInfo fieldInfo;
  final long sumTotalTermFreq;
  final long sumDocFreq;
  final int docCount;
  final long indexStartFP;
  final long rootBlockFP;
  final Output rootCode;
  final BytesRef minTerm;
  final BytesRef maxTerm;
  final OrdsBlockTreeTermsReader parent;

  final FST<Output> index;

  // private boolean DEBUG;

  OrdsFieldReader(
      OrdsBlockTreeTermsReader parent,
      FieldInfo fieldInfo,
      long numTerms,
      Output rootCode,
      long sumTotalTermFreq,
      long sumDocFreq,
      int docCount,
      long indexStartFP,
      IndexInput indexIn,
      BytesRef minTerm,
      BytesRef maxTerm)
      throws IOException {
    assert numTerms > 0;
    this.fieldInfo = fieldInfo;
    // DEBUG = BlockTreeTermsReader.DEBUG && fieldInfo.name.equals("id");
    this.parent = parent;
    this.numTerms = numTerms;
    this.sumTotalTermFreq = sumTotalTermFreq;
    this.sumDocFreq = sumDocFreq;
    this.docCount = docCount;
    this.indexStartFP = indexStartFP;
    this.rootCode = rootCode;
    this.minTerm = minTerm;
    this.maxTerm = maxTerm;
    // if (DEBUG) {
    //   System.out.println("BTTR: seg=" + segment + " field=" + fieldInfo.name + " rootBlockCode="
    // + rootCode + " divisor=" + indexDivisor);
    // }

    rootBlockFP =
        (new ByteArrayDataInput(rootCode.bytes.bytes, rootCode.bytes.offset, rootCode.bytes.length))
                .readVLong()
            >>> OrdsBlockTreeTermsWriter.OUTPUT_FLAGS_NUM_BITS;

    if (indexIn != null) {
      final IndexInput clone = indexIn.clone();
      // System.out.println("start=" + indexStartFP + " field=" + fieldInfo.name);
      clone.seek(indexStartFP);
      index = new FST<>(clone, clone, OrdsBlockTreeTermsWriter.FST_OUTPUTS);

      /*
      if (true) {
        final String dotFileName = "/tmp/" + parent.segment + "_" + fieldInfo.name + ".dot";
        Writer w = new OutputStreamWriter(new FileOutputStream(dotFileName));
        Util.toDot(index, w, false, false);
        System.out.println("FST INDEX: SAVED to " + dotFileName);
        w.close();
      }
      */
    } else {
      index = null;
    }
  }

  @Override
  public BytesRef getMin() throws IOException {
    if (minTerm == null) {
      // Older index that didn't store min/maxTerm
      return super.getMin();
    } else {
      return minTerm;
    }
  }

  @Override
  public BytesRef getMax() throws IOException {
    if (maxTerm == null) {
      // Older index that didn't store min/maxTerm
      return super.getMax();
    } else {
      return maxTerm;
    }
  }

  @Override
  public boolean hasFreqs() {
    return fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
  }

  @Override
  public boolean hasOffsets() {
    return fieldInfo
            .getIndexOptions()
            .compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        >= 0;
  }

  @Override
  public boolean hasPositions() {
    return fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
  }

  @Override
  public boolean hasPayloads() {
    return fieldInfo.hasPayloads();
  }

  @Override
  public TermsEnum iterator() throws IOException {
    return new OrdsSegmentTermsEnum(this);
  }

  @Override
  public long size() {
    return numTerms;
  }

  @Override
  public long getSumTotalTermFreq() {
    return sumTotalTermFreq;
  }

  @Override
  public long getSumDocFreq() {
    return sumDocFreq;
  }

  @Override
  public int getDocCount() {
    return docCount;
  }

  @Override
  public TermsEnum intersect(CompiledAutomaton compiled, BytesRef startTerm) throws IOException {
    if (compiled.type != CompiledAutomaton.AUTOMATON_TYPE.NORMAL) {
      throw new IllegalArgumentException("please use CompiledAutomaton.getTermsEnum instead");
    }
    return new OrdsIntersectTermsEnum(this, compiled, startTerm);
  }

  @Override
  public String toString() {
    return "OrdsBlockTreeTerms(terms="
        + numTerms
        + ",postings="
        + sumDocFreq
        + ",positions="
        + sumTotalTermFreq
        + ",docs="
        + docCount
        + ")";
  }
}
