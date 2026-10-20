package com.sheetmusic4j.core.lilypond;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Score;

/**
 * Reads a LilyPond {@code .ly} document into a {@link Score}.
 *
 * <p>Reading happens in three steps: {@link LilyPondParser} parses the text
 * into a music tree (resolving the note-name language, default durations and
 * variables), {@link LilyPondInterpreter} runs it through time and builds the
 * context tree (staff groups, staves, voices, lyrics, chord names), and
 * {@link LilyPondScoreAssembler} cuts the result into measures. The first
 * {@code \score} of the file is read (or a top-level music expression).
 *
 * <p>Supported: {@code \relative}/{@code \fixed}/{@code \transpose}, all note
 * name languages, chords and {@code q}, ties, slurs, beams (explicit and
 * automatic), tuplets, grace notes, {@code \repeat} (volta with alternatives,
 * unfold, percent, tremolo), {@code \partial}, {@code \cadenzaOn}, multiple
 * staves and voices ({@code << \\ >>}, {@code \new Voice}, {@code \change
 * Staff}), piano staves and staff groups, instrument names, lyrics
 * ({@code \addlyrics}, {@code \lyricsto}), chord names, dynamics, hairpins,
 * text scripts, tempo and rehearsal marks, {@code \tag} filtering and
 * {@code \include} of files next to the score. Engraving tweaks
 * ({@code \override}, {@code \tweak}, Scheme code) are skipped.
 */
public final class LilyPondReader implements ScoreReader {

    private static final int MAX_INCLUDE_DEPTH = 8;
    private static final Pattern INCLUDE = Pattern.compile("\\\\include\\s*\"([^\"]*)\"");

    @Override
    public Score read(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LilyPondException("Could not read LilyPond file: " + path, e);
        }
        Path dir = path.toAbsolutePath().normalize().getParent();
        return parse(text, dir);
    }

    @Override
    public Score read(InputStream in) {
        try {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            throw new LilyPondException("Failed to read LilyPond stream", e);
        }
    }

    Score parse(String rawText) {
        return parse(rawText, null);
    }

    private Score parse(String rawText, Path baseDir) {
        String text = expandIncludes(stripComments(rawText), baseDir, baseDir, 0);
        LilyPondParser.Document document = LilyPondParser.parse(text);

        Map<String, String> header = new LinkedHashMap<>(document.header());
        Score.Builder score = Score.builder();
        if (!document.scores().isEmpty()) {
            LilyPondParser.ScoreBlock first = document.scores().get(0);
            first.header().forEach(header::putIfAbsent);
            LilyPondInterpreter.Result result = LilyPondInterpreter.interpret(first.music(), document.contextTypes());
            result.global().timeSignaturesHidden |= document.timeSignaturesHidden();
            LilyPondScoreAssembler.assemble(result, score);
        }
        applyHeader(header, score);
        return score.build();
    }

    private static void applyHeader(Map<String, String> header, Score.Builder score) {
        String title = header.get("title");
        if (title != null) {
            score.workTitle(title);
        }
        String subtitle = header.get("subtitle");
        if (subtitle == null) {
            subtitle = header.get("piece");
        }
        if (subtitle != null) {
            score.movementTitle(subtitle);
        }
        for (String role : new String[]{"composer", "arranger", "poet", "lyricist"}) {
            Creator creator = Creator.of(role, header.get(role));
            if (creator != null) {
                score.addCreator(creator);
            }
        }
    }

    /**
     * Replace {@code \include "file"} with the file's content when it is a
     * file next to the score (never outside its directory), or with a
     * {@code \language} switch for LilyPond's language files.
     */
    private static String expandIncludes(String text, Path dir, Path root, int depth) {
        Matcher m = INCLUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = "";
            String language = LilyPondPitch.language(name);
            if (language != null) {
                replacement = "\\language \"" + language + "\"";
            } else if (dir != null && root != null && depth < MAX_INCLUDE_DEPTH) {
                Path file = dir.resolve(name).normalize();
                if (file.startsWith(root) && Files.isRegularFile(file)) {
                    try {
                        String included = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                        replacement = expandIncludes(included, file.getParent(), root, depth + 1);
                    } catch (IOException e) {
                        replacement = "";
                    }
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Strip line ({@code % ...}) and block ({@code %{ ... %}}) comments, leaving strings intact. */
    static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int j = i + 1;
                while (j < n && text.charAt(j) != '"') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                out.append(text, i, j);
                i = j;
                continue;
            }
            if (c == '%') {
                if (i + 1 < n && text.charAt(i + 1) == '{') {
                    int end = text.indexOf("%}", i + 2);
                    i = end < 0 ? n : end + 2;
                    out.append(' ');
                    continue;
                }
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
