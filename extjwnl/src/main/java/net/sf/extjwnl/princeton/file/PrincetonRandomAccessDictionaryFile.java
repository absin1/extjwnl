package net.sf.extjwnl.princeton.file;

import net.sf.extjwnl.JWNL;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.JWNLRuntimeException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.dictionary.Dictionary;
import net.sf.extjwnl.dictionary.file.DictionaryFileFactory;
import net.sf.extjwnl.dictionary.file.DictionaryFileType;
import net.sf.extjwnl.util.factory.Param;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.MalformedInputException;
import java.text.DecimalFormat;
import java.util.*;

/**
 * A <code>RandomAccessDictionaryFile</code> that accesses files
 * named with Princeton's dictionary file naming convention.
 *
 * @author John Didion <jdidion@didion.net>
 * @author Aliaksandr Autayeu <avtaev@gmail.com>
 */
public class PrincetonRandomAccessDictionaryFile extends AbstractPrincetonRandomAccessDictionaryFile implements DictionaryFileFactory<PrincetonRandomAccessDictionaryFile> {

    private static final Log log = LogFactory.getLog(PrincetonRandomAccessDictionaryFile.class);

    private static final String PRINCETON_HEADER = "  1 This software and database is being provided to you, the LICENSEE, by  \n" +
            "  2 Princeton University under the following license.  By obtaining, using  \n" +
            "  3 and/or copying this software and database, you agree that you have  \n" +
            "  4 read, understood, and will comply with these terms and conditions.:  \n" +
            "  5   \n" +
            "  6 Permission to use, copy, modify and distribute this software and  \n" +
            "  7 database and its documentation for any purpose and without fee or  \n" +
            "  8 royalty is hereby granted, provided that you agree to comply with  \n" +
            "  9 the following copyright notice and statements, including the disclaimer,  \n" +
            "  10 and that the same appear on ALL copies of the software, database and  \n" +
            "  11 documentation, including modifications that you make for internal  \n" +
            "  12 use or for distribution.  \n" +
            "  13   \n" +
            "  14 WordNet 3.0 Copyright 2006 by Princeton University.  All rights reserved.  \n" +
            "  15   \n" +
            "  16 THIS SOFTWARE AND DATABASE IS PROVIDED \"AS IS\" AND PRINCETON  \n" +
            "  17 UNIVERSITY MAKES NO REPRESENTATIONS OR WARRANTIES, EXPRESS OR  \n" +
            "  18 IMPLIED.  BY WAY OF EXAMPLE, BUT NOT LIMITATION, PRINCETON  \n" +
            "  19 UNIVERSITY MAKES NO REPRESENTATIONS OR WARRANTIES OF MERCHANT-  \n" +
            "  20 ABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE  \n" +
            "  21 OF THE LICENSED SOFTWARE, DATABASE OR DOCUMENTATION WILL NOT  \n" +
            "  22 INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR  \n" +
            "  23 OTHER RIGHTS.  \n" +
            "  24   \n" +
            "  25 The name of Princeton University or Princeton may not be used in  \n" +
            "  26 advertising or publicity pertaining to distribution of the software  \n" +
            "  27 and/or database.  Title to copyright in this software, database and  \n" +
            "  28 any associated documentation shall at all times remain with  \n" +
            "  29 Princeton University and LICENSEE agrees to preserve same.  \n";

    /**
     * Whether to add standard princeton header to files on save, default: false.
     */
    public static final String WRITE_PRINCETON_HEADER_KEY = "write_princeton_header";
    private boolean writePrincetonHeader = false;

    /**
     * Whether to warn about lex file numbers correctness, default: true.
     */
    public static final String CHECK_LEX_FILE_NUMBER_KEY = "check_lex_file_number";
    private boolean checkLexFileNumber = true;

    /**
     * Whether to warn about relation count being off limits, default: true.
     */
    public static final String CHECK_RELATION_LIMIT_KEY = "check_relation_limit";
    private boolean checkRelationLimit = true;

    /**
     * Whether to warn about offsets being off limits, default: true.
     */
    public static final String CHECK_OFFSET_LIMIT_KEY = "check_offset_limit";
    private boolean checkOffsetLimit = true;

    /**
     * Whether to warn about word count being off limits, default: true.
     */
    public static final String CHECK_WORD_COUNT_LIMIT_KEY = "check_word_count_limit";
    private boolean checkWordCountLimit = true;

    /**
     * Whether to warn about lex id being off limits, default: true.
     */
    public static final String CHECK_LEX_ID_LIMIT_KEY = "check_lex_id_limit";
    private boolean checkLexIdLimit = true;

    /**
     * Whether to warn about pointer target indices being off limits, default: true
     */
    public static final String CHECK_POINTER_INDEX_LIMIT_KEY = "check_pointer_index_limit";
    private boolean checkPointerIndexLimit = true;

    /**
     * Whether to warn about verb frame indices being off limits, default: true
     */
    public static final String CHECK_VERB_FRAME_LIMIT_KEY = "check_verb_frame_limit";
    private boolean checkVerbFrameLimit = true;

    /**
     * Read-only file permission.
     */
    public static final String READ_ONLY = "r";
    /**
     * Read-write file permission.
     */
    public static final String READ_WRITE = "rw";

    /**
     * The random-access file.
     */
    protected RandomAccessFile raFile = null;

    private CharsetDecoder decoder;

    private int LINE_MAX = 1024;//1K buffer
    private byte[] lineArr = new byte[LINE_MAX];

    private DecimalFormat dfOff;
    private String decimalFormatString = "00000000";

    public PrincetonRandomAccessDictionaryFile(Dictionary dictionary, Map<String, Param> params) {
        super(dictionary, params);
    }

    public PrincetonRandomAccessDictionaryFile(Dictionary dictionary, String path, POS pos, DictionaryFileType fileType, Map<String, Param> params) {
        super(dictionary, path, pos, fileType, params);
        if (null != encoding) {
            Charset charset = Charset.forName(encoding);
            decoder = charset.newDecoder();
        }
        if (params.containsKey(WRITE_PRINCETON_HEADER_KEY)) {
            writePrincetonHeader = Boolean.parseBoolean(params.get(WRITE_PRINCETON_HEADER_KEY).getValue());
        }
        if (params.containsKey(CHECK_LEX_FILE_NUMBER_KEY)) {
            checkLexFileNumber = Boolean.parseBoolean(params.get(CHECK_LEX_FILE_NUMBER_KEY).getValue());
        }
        if (params.containsKey(CHECK_RELATION_LIMIT_KEY)) {
            checkRelationLimit = Boolean.parseBoolean(params.get(CHECK_RELATION_LIMIT_KEY).getValue());
        }
        if (params.containsKey(CHECK_OFFSET_LIMIT_KEY)) {
            checkOffsetLimit = Boolean.parseBoolean(params.get(CHECK_OFFSET_LIMIT_KEY).getValue());
        }
        if (params.containsKey(CHECK_WORD_COUNT_LIMIT_KEY)) {
            checkWordCountLimit = Boolean.parseBoolean(params.get(CHECK_WORD_COUNT_LIMIT_KEY).getValue());
        }
        if (params.containsKey(CHECK_LEX_ID_LIMIT_KEY)) {
            checkLexIdLimit = Boolean.parseBoolean(params.get(CHECK_LEX_ID_LIMIT_KEY).getValue());
        }
        if (params.containsKey(CHECK_POINTER_INDEX_LIMIT_KEY)) {
            checkPointerIndexLimit = Boolean.parseBoolean(params.get(CHECK_POINTER_INDEX_LIMIT_KEY).getValue());
        }
        if (params.containsKey(CHECK_VERB_FRAME_LIMIT_KEY)) {
            checkVerbFrameLimit = Boolean.parseBoolean(params.get(CHECK_VERB_FRAME_LIMIT_KEY).getValue());
        }
    }

    public PrincetonRandomAccessDictionaryFile newInstance(Dictionary dictionary, String path, POS pos, DictionaryFileType fileType) {
        return new PrincetonRandomAccessDictionaryFile(dictionary, path, pos, fileType, params);
    }

    public String readLine() throws IOException {
        if (isOpen()) {
            if (null == encoding) {
                return raFile.readLine();
            } else {
                int c = -1;
                boolean eol = false;
                int idx = 1;
                StringBuilder input = new StringBuilder();

                while (!eol) {
                    switch (c = read()) {
                        case -1:
                        case '\n':
                            eol = true;
                            break;
                        case '\r':
                            eol = true;
                            long cur = getFilePointer();
                            if ((read()) != '\n') {
                                seek(cur);
                            }
                            break;
                        default: {
                            lineArr[idx - 1] = (byte) c;
                            input.append((char) c);
                            idx++;
                            if (LINE_MAX == idx) {
                                byte[] t = new byte[LINE_MAX * 2];
                                System.arraycopy(lineArr, 0, t, 0, LINE_MAX);
                                lineArr = t;
                                LINE_MAX = 2 * LINE_MAX;
                            }
                            break;
                        }
                    }
                }

                if ((c == -1) && (input.length() == 0)) {
                    return null;
                }
                if (1 < idx) {
                    ByteBuffer bb = ByteBuffer.wrap(lineArr, 0, idx - 1);
                    try {
                        CharBuffer cb = decoder.decode(bb);
                        return cb.toString();
                    } catch (MalformedInputException e) {
                        return " ";
                    }
                } else {
                    return input.toString();
                }
            }
        } else {
            throw new JWNLRuntimeException("PRINCETON_EXCEPTION_001");
        }
    }

    public String readLineWord() throws IOException {
        if (isOpen()) {
            //in data files offset needs no decoding, it is numeric
            if (null == encoding || getFileType().equals(DictionaryFileType.DATA)) {
                StringBuffer input = new StringBuffer();
                int c;
                while (((c = raFile.read()) != -1) && c != '\n' && c != '\r' && c != ' ') {
                    input.append((char) c);
                }
                return input.toString();
            } else {
                int idx = 1;
                int c;
                while (((c = raFile.read()) != -1) && c != '\n' && c != '\r' && c != ' ') {
                    lineArr[idx - 1] = (byte) c;
                    idx++;
                    if (LINE_MAX == idx) {
                        byte[] t = new byte[LINE_MAX * 2];
                        System.arraycopy(lineArr, 0, t, 0, LINE_MAX);
                        lineArr = t;
                        LINE_MAX = 2 * LINE_MAX;
                    }
                }
                if (1 < idx) {
                    ByteBuffer bb = ByteBuffer.wrap(lineArr, 0, idx - 1);
                    CharBuffer cb = decoder.decode(bb);
                    return cb.toString();
                } else {
                    return "";
                }
            }
        } else {
            throw new JWNLRuntimeException("PRINCETON_EXCEPTION_001");
        }
    }

    public void seek(long pos) throws IOException {
        raFile.seek(pos);
    }

    public long getFilePointer() throws IOException {
        return raFile.getFilePointer();
    }

    public boolean isOpen() {
        return raFile != null;
    }

    public void close() {
        try {
            if (null != raFile) {
                raFile.close();
            }
            super.close();
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(JWNL.resolveMessage("EXCEPTION_001", e.getMessage()), e);
            }
        } finally {
            raFile = null;
        }
    }

    /**
     * Here we try to be intelligent about opening files.
     * If the file does not already exist, we assume that we are going
     * to be creating it and writing to it, otherwise we assume that
     * we are going to be reading from it.
     */
    protected void openFile() throws IOException {
        if (!file.exists()) {
            raFile = new RandomAccessFile(file, READ_WRITE);
        } else {
            raFile = new RandomAccessFile(file, READ_ONLY);
        }

    }

    public void edit() throws IOException {
        raFile.close();
        raFile = new RandomAccessFile(file, READ_WRITE);
    }


    public long length() throws IOException {
        return raFile.length();
    }

    public int read() throws IOException {
        return raFile.read();
    }

    public void save() throws IOException, JWNLException {
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_004", getFilename()));
        }
        if (DictionaryFileType.EXCEPTION.equals(getFileType())) {
            ArrayList<String> exceptions = new ArrayList<String>();
            Iterator<Exc> ei = dictionary.getExceptionIterator(getPOS());
            while (ei.hasNext()) {
                exceptions.add(renderException(ei.next()));
            }
            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_005", exceptions.size()));
            }
            Collections.sort(exceptions);

            seek(0);
            writeStrings(exceptions);
        } else if (DictionaryFileType.DATA.equals(getFileType())) {
            ArrayList<Synset> synsets = new ArrayList<Synset>();
            Iterator<Synset> si = dictionary.getSynsetIterator(getPOS());
            while (si.hasNext()) {
                synsets.add(si.next());
            }

            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_005", synsets.size()));
            }
            Collections.sort(synsets, new Comparator<Synset>() {
                public int compare(Synset o1, Synset o2) {
                    return (int) Math.signum(o1.getOffset() - o2.getOffset());
                }
            });

            dfOff = new DecimalFormat("00000000");//8 by default
            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_006", synsets.size()));
            }
            long offset = 0;
            if (writePrincetonHeader) {
                offset = offset + PRINCETON_HEADER.length();
                if (checkOffsetLimit && log.isWarnEnabled() && (99999999 < offset)) {
                    log.warn(JWNL.resolveMessage("PRINCETON_WARN_003", offset));
                }
            }
            for (Synset s : synsets) {
                s.setOffset(offset);
                if (null == encoding) {
                    offset = offset + renderSynset(s).getBytes().length + 1;//\n should be 1 byte
                } else {
                    offset = offset + renderSynset(s).getBytes(encoding).length + 1;//\n should be 1 byte
                }
            }
            //calculate offset length
            decimalFormatString = createOffsetFormatString(offset);
            dfOff = new DecimalFormat(decimalFormatString);//there is a small chance another update might be necessary

            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_007", synsets.size()));
            }
            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_008", getFilename()));
            }
            long counter = 0;
            long total = synsets.size();
            long reportInt = (total / 20) + 1;//i.e. report every 5%
            seek(0);
            if (writePrincetonHeader) {
                if (log.isInfoEnabled()) {
                    log.info(JWNL.resolveMessage("PRINCETON_INFO_020", getFilename()));
                }
                raFile.writeBytes(PRINCETON_HEADER);
            }
            for (Synset synset : synsets) {
                counter++;
                if (0 == (counter % reportInt)) {
                    if (log.isInfoEnabled()) {
                        log.info(JWNL.resolveMessage("PRINCETON_INFO_014", 100 * counter / total));
                    }
                }
                if (null == encoding) {
                    raFile.write(renderSynset(synset).getBytes());
                } else {
                    raFile.write(renderSynset(synset).getBytes(encoding));
                }
                raFile.writeBytes("\n");
            }
            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_009", getFilename()));
            }

        } else if (DictionaryFileType.INDEX.equals(getFileType())) {
            ArrayList<String> indexes = new ArrayList<String>();

            Iterator<IndexWord> ii = dictionary.getIndexWordIterator(getPOS());
            long maxOffset = 0;
            while (ii.hasNext()) {
                IndexWord indexWord = ii.next();
                for (Synset synset : indexWord.getSenses()) {
                    if (maxOffset < synset.getOffset()) {
                        maxOffset = synset.getOffset();
                    }
                }
            }
            decimalFormatString = createOffsetFormatString(maxOffset);
            dfOff = new DecimalFormat(decimalFormatString);

            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_011", getFilename()));
            }
            ii = dictionary.getIndexWordIterator(getPOS());
            while (ii.hasNext()) {
                indexes.add(renderIndexWord(ii.next()));
            }

            if (log.isInfoEnabled()) {
                log.info(JWNL.resolveMessage("PRINCETON_INFO_005", indexes.size()));
            }
            Collections.sort(indexes);

            seek(0);
            if (writePrincetonHeader) {
                raFile.writeBytes(PRINCETON_HEADER);
            }
            writeIndexStrings(indexes);
        }
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_012", getFilename()));
        }
    }

    @Override
    public void writeLine(String line) throws IOException {
        if (null == encoding) {
            raFile.write(line.getBytes());
        } else {
            raFile.write(line.getBytes(encoding));
        }
        raFile.writeBytes("\n");

    }

    public void writeStrings(Collection<String> strings) throws IOException {
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_008", getFilename()));
        }
        long counter = 0;
        long total = strings.size();
        long reportInt = (total / 20) + 1;//i.e. report every 5%
        for (String s : strings) {
            counter++;
            if (0 == (counter % reportInt)) {
                if (log.isInfoEnabled()) {
                    log.info(JWNL.resolveMessage("PRINCETON_INFO_014", 100 * counter / total));
                }
            }
            writeLine(s);
        }
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_013", getFilename()));
        }
    }

    public void writeIndexStrings(ArrayList<String> strings) throws IOException {
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_008", getFilename()));
        }
        long counter = 0;
        long total = strings.size();
        long reportInt = (total / 20) + 1;//i.e. report every 5%
        //see makedb.c FixLastRecord
        /* Funky routine to pad the second to the last record of the
           index file to be longer than the last record so the binary
           search in the search code works properly. */
        for (int i = 0; i < strings.size() - 2; i++) {
            counter++;
            if (0 == (counter % reportInt)) {
                if (log.isInfoEnabled()) {
                    log.info(JWNL.resolveMessage("PRINCETON_INFO_014", 100 * counter / total));
                }
            }
            writeLine(strings.get(i));
        }
        if (1 < strings.size()) {
            String nextToLast = strings.get(strings.size() - 2);
            String last = strings.get(strings.size() - 1);
            while (nextToLast.length() <= last.length()) {
                nextToLast = nextToLast + " ";
            }
            writeLine(nextToLast);
            writeLine(last);
        }
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_014", 100));
        }
        if (log.isInfoEnabled()) {
            log.info(JWNL.resolveMessage("PRINCETON_INFO_013", getFilename()));
        }
    }


    @Override
    public String getOffsetFormatString() {
        return decimalFormatString;
    }

    private String createOffsetFormatString(long offset) {
        int offsetLength = 0;
        while (0 < offset) {
            offset = offset / 10;
            offsetLength++;
        }
        offsetLength = Math.max(8, offsetLength);
        StringBuilder formatString = new StringBuilder();
        while (0 < offsetLength) {
            formatString.append("0");
            offsetLength--;
        }
        return formatString.toString();
    }

    private String renderSynset(Synset synset) {
        //synset_offset  lex_filenum  ss_type  w_cnt  word  lex_id  [word  lex_id...]  p_cnt  [ptr...]  [frames...]  |   gloss
        //w_cnt Two digit hexadecimal integer indicating the number of words in the synset.
        String posKey = synset.getPOS().getKey();
        if (synset.isAdjectiveCluster()) {
            posKey = POS.ADJECTIVE_SATELLITE.getKey();
        }
        if (checkLexFileNumber && log.isWarnEnabled() && !LexFileIdFileNameMap.getMap().containsKey(synset.getLexFileNum())) {
            log.warn(JWNL.resolveMessage("PRINCETON_WARN_001", synset.getLexFileNum()));
        }
        if (checkWordCountLimit && log.isWarnEnabled() && (0xFF < synset.getWords().size())) {
            log.warn(JWNL.resolveMessage("PRINCETON_WARN_004", new Object[]{synset.getOffset(), synset.getWords().size()}));
        }
        StringBuilder result = new StringBuilder(String.format("%s %02d %s %02x ", dfOff.format(synset.getOffset()), synset.getLexFileNum(), posKey, synset.getWords().size()));
        for (Word w : synset.getWords()) {
            //ASCII form of a word as entered in the synset by the lexicographer, with spaces replaced by underscore characters (_ ). The text of the word is case sensitive.
            //lex_id One digit hexadecimal integer that, when appended onto lemma , uniquely identifies a sense within a lexicographer file.
            String lemma = w.getLemma().replace(' ', '_');
            if (w instanceof Adjective) {
                Adjective a = (Adjective) w;
                if (!Adjective.NONE.equals(a.getAdjectivePosition())) {
                    lemma = lemma + "(" + a.getAdjectivePosition().getKey() + ")";
                }
            }
            if (checkLexIdLimit && log.isWarnEnabled() && (0xF < w.getLexId())) {
                log.warn(JWNL.resolveMessage("PRINCETON_WARN_005", new Object[]{synset.getOffset(), w.getLemma(), w.getLexId()}));
            }
            result.append(String.format("%s %x ", lemma, w.getLexId()));
        }
        //Three digit decimal integer indicating the number of pointers from this synset to other synsets. If p_cnt is 000 the synset has no pointers.
        if (checkRelationLimit && log.isWarnEnabled() && (999 < synset.getPointers().size())) {
            log.warn(JWNL.resolveMessage("PRINCETON_WARN_002", new Object[]{synset.getOffset(), synset.getPointers().size()}));
        }
        result.append(String.format("%03d ", synset.getPointers().size()));
        for (Pointer p : synset.getPointers()) {
            //pointer_symbol  synset_offset  pos  source/target
            result.append(p.getType().getKey()).append(" ");
            //synset_offset is the byte offset of the target synset in the data file corresponding to pos
            result.append(dfOff.format(p.getTargetOffset())).append(" ");
            //pos
            result.append(p.getTargetPOS().getKey()).append(" ");
            //source/target
            //The source/target field distinguishes lexical and semantic pointers.
            // It is a four byte field, containing two two-digit hexadecimal integers.
            // The first two digits indicates the word number in the current (source) synset,
            // the last two digits indicate the word number in the target synset.
            // A value of 0000 means that pointer_symbol represents a semantic relation between the current (source) synset and the target synset indicated by synset_offset .

            //A lexical relation between two words in different synsets is represented by non-zero values in the source and target word numbers.
            // The first and last two bytes of this field indicate the word numbers in the source and target synsets, respectively, between which the relation holds.
            // Word numbers are assigned to the word fields in a synset, from left to right, beginning with 1 .
            if (checkPointerIndexLimit && log.isWarnEnabled() && (0xFF < p.getSourceIndex())) {
                log.warn(JWNL.resolveMessage("PRINCETON_WARN_006", new Object[]{synset.getOffset(), p.getSource().getSynset().getOffset(), p.getSourceIndex()}));
            }
            if (checkPointerIndexLimit && log.isWarnEnabled() && (0xFF < p.getTargetIndex())) {
                log.warn(JWNL.resolveMessage("PRINCETON_WARN_006", new Object[]{synset.getOffset(), p.getTarget().getSynset().getOffset(), p.getTargetIndex()}));
            }
            result.append(String.format("%02x%02x ", p.getSourceIndex(), p.getTargetIndex()));
        }

        //frames In data.verb only
        if (POS.VERB.equals(synset.getPOS())) {
            BitSet verbFrames = synset.getVerbFrameFlags();
            int verbFramesCount = verbFrames.cardinality();
            for (Word word : synset.getWords()) {
                if (word instanceof Verb) {
                    BitSet bits = ((Verb) word).getVerbFrameFlags();
                    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                        //WN TRICK - there are duplicates in data
                        //02593551 41 v 04 lord_it_over 0 queen_it_over 0 put_on_airs 0 act_superior 0 001 @ 02367363 v 0000
                        // 09 + 02 00 + 02 04 + 22 04 + 02 03 + 22 03 + 08 02 + 09 02 + 08 01 + 09 01 | act like the master of; "He is lording it over the students"
                        // + 02 04 and + 02 03 duplicate + 02 00
                        // it is the only one, but it causes offsets to differ on WN30 rewrite
                        if (!verbFrames.get(i)) {
                            verbFramesCount++;
                        }
                    }
                }
            }
            if (checkVerbFrameLimit && log.isWarnEnabled() && (99 < verbFramesCount)) {
                log.warn(JWNL.resolveMessage("PRINCETON_WARN_007", new Object[]{synset.getOffset(), verbFramesCount}));
            }
            result.append(String.format("%02d ", verbFramesCount));
            for (int i = verbFrames.nextSetBit(0); i >= 0; i = verbFrames.nextSetBit(i + 1)) {
                if (checkVerbFrameLimit && log.isWarnEnabled() && (99 < i)) {
                    log.warn(JWNL.resolveMessage("PRINCETON_WARN_008", new Object[]{synset.getOffset(), i}));
                }
                result.append(String.format("+ %02d 00 ", i));
            }
            for (Word word : synset.getWords()) {
                if (word instanceof Verb) {
                    BitSet bits = ((Verb) word).getVerbFrameFlags();
                    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                        if (!verbFrames.get(i)) {
                            if (checkVerbFrameLimit && log.isWarnEnabled() && (0xFF < word.getIndex())) {
                                log.warn(JWNL.resolveMessage("PRINCETON_WARN_008", new Object[]{synset.getOffset(), word.getIndex()}));
                            }
                            result.append(String.format("+ %02d %02x ", i, word.getIndex()));
                        }
                    }
                }
            }
        }

        result.append("| ").append(synset.getGloss()).append("  ");//why every line in most WN files ends with two spaces?

        return result.toString();
    }

    private String renderIndexWord(IndexWord indexWord) {
        //lemma  pos  synset_cnt  p_cnt  [ptr_symbol...]  sense_cnt  tagsense_cnt   synset_offset  [synset_offset...]
        StringBuilder result = new StringBuilder(indexWord.getLemma().replace(' ', '_'));
        result.append(" ");
        result.append(indexWord.getPOS().getKey()).append(" ");//pos
        result.append(Integer.toString(indexWord.getSenses().size())).append(" ");//synset_cnt
        ArrayList<PointerType> pointerTypes = new ArrayList<PointerType>();
        //find all the pointers that come from this word
        for (Synset synset : indexWord.getSenses()) {
            for (Pointer pointer : synset.getPointers()) {
                if (pointer.isLexical() && !indexWord.getLemma().equals(((Word) pointer.getSource()).getLemma().toLowerCase())) {
                    continue;
                }
                //WN TRICK
                //see makedb.c line 370
                PointerType pt = pointer.getType();
                char c = pointer.getType().getKey().charAt(0);
                if (';' == c || '-' == c || '@' == c || '~' == c) {
                    pt = PointerType.getPointerTypeForKey(Character.toString(c));
                }
                if (!pointerTypes.contains(pt)) {
                    pointerTypes.add(pt);
                }
            }
        }

        Collections.sort(pointerTypes);
        result.append(Integer.toString(pointerTypes.size())).append(" ");//p_cnt
        for (PointerType pointerType : pointerTypes) {
            result.append(pointerType.getKey()).append(" ");
        }

        result.append(Integer.toString(indexWord.getSenses().size())).append(" ");//sense_cnt

        //sort senses and find out tagged sense count
        int tagSenseCnt = indexWord.sortSenses();
        result.append(Integer.toString(tagSenseCnt)).append(" ");//tagsense_cnt

        for (Synset synset : indexWord.getSenses()) {
            result.append(dfOff.format(synset.getOffset())).append(" ");//synset_offset
        }

        result.append(" ");
        return result.toString();
    }

    private String renderException(Exc exc) {
        StringBuilder result = new StringBuilder();
        result.append(exc.getLemma().replace(' ', '_'));
        for (String e : exc.getExceptions()) {
            result.append(" ").append(e.replace(' ', '_'));
        }
        return result.toString();
    }
}