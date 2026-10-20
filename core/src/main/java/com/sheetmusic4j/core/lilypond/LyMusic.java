package com.sheetmusic4j.core.lilypond;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Step;

/**
 * A parsed LilyPond music expression. {@link LilyPondParser} builds the tree
 * with everything that is lexical already resolved (note-name language,
 * default durations, variables); {@link LilyPondInterpreter} gives it musical
 * meaning (time, contexts, relative octaves).
 *
 * <p>Durations are in quarter notes.
 */
sealed interface LyMusic {

    /** Sequential music: {@code { a b c }}. */
    record Seq(List<LyMusic> items) implements LyMusic {
    }

    /**
     * Simultaneous music: {@code << a b >>}. With {@code voices} set it was
     * written {@code << a \\ b >>}, and each item is a separate voice.
     */
    record Sim(List<LyMusic> items, boolean voices) implements LyMusic {
    }

    /** {@code \new Type = "name" \with { ... } music} or {@code \context ...}. */
    record Context(String type, String name, boolean create, Map<String, String> with, LyMusic body)
            implements LyMusic {
    }

    record Relative(RawPitch reference, LyMusic body) implements LyMusic {
    }

    record Fixed(RawPitch reference, LyMusic body) implements LyMusic {
    }

    record Transpose(RawPitch from, RawPitch to, LyMusic body) implements LyMusic {
    }

    /**
     * {@code \tuplet actual/normal [span] { ... }}: {@code actual} notes in the
     * time of {@code normal}; with a span, one bracket per span of time.
     */
    record Tuplet(int actual, int normal, Fraction span, LyMusic body) implements LyMusic {
    }

    /** {@code \scaleDurations n/d { ... }}: scaled time, no bracket. */
    record Scale(Fraction factor, LyMusic body) implements LyMusic {
    }

    /** {@code \repeat type count body \alternative { ... }}. */
    record Repeat(String type, int count, LyMusic body, List<LyMusic> alternatives) implements LyMusic {
    }

    record Grace(LyMusic body) implements LyMusic {
    }

    /** {@code \keepWithTag} / {@code \removeWithTag}. */
    record TagFilter(Set<String> tags, boolean keep, LyMusic body) implements LyMusic {
    }

    /** {@code \tag #'name music}. */
    record Tagged(Set<String> tags, LyMusic body) implements LyMusic {
    }

    /** Music followed by one or more {@code \addlyrics} blocks. */
    record AddLyrics(LyMusic music, List<Lyrics> lyrics) implements LyMusic {
    }

    /** {@code \lyricsto "voice" { ... }}. */
    record LyricsTo(String voice, Lyrics lyrics) implements LyMusic {
    }

    /** Lyric-mode music. */
    record Lyrics(List<Syllable> syllables) implements LyMusic {
    }

    /**
     * One lyric syllable; {@code hyphen} when a {@code --} follows it,
     * {@code skip} for {@code _} / {@code \skip} (a note without text).
     */
    record Syllable(String text, boolean hyphen, boolean skip) {

        static Syllable of(String text) {
            return new Syllable(text, false, false);
        }

        static Syllable skipped() {
            return new Syllable("", false, true);
        }

        Syllable withHyphen() {
            return new Syllable(text, true, skip);
        }
    }

    /** One chord-mode chord ({@code harmony} null for a rest or skip). */
    record ChordSymbol(Harmony harmony, Fraction length) implements LyMusic {
    }

    /** A note, chord, rest or skip. */
    record Event(Kind kind, List<EventPitch> pitches, NoteType type, int dots, Fraction length,
                 List<Post> post) implements LyMusic {

        enum Kind {
            NOTE,
            /** A note with only a duration: repeats the previous pitch. */
            REPEAT_NOTE,
            /** {@code q}: repeats the previous chord. */
            REPEAT_CHORD,
            REST,
            MEASURE_REST,
            SKIP
        }
    }

    record TimeSig(int beats, int beatType) implements LyMusic {
    }

    record KeySig(RawPitch tonic, String mode) implements LyMusic {
    }

    record ClefChange(String name) implements LyMusic {
    }

    record BarLine(String style) implements LyMusic {
    }

    record Partial(Fraction length) implements LyMusic {
    }

    /** {@code \tempo}: an optional text and an optional metronome mark. */
    record Tempo(String text, NoteType unit, int dots, int perMinute) implements LyMusic {
    }

    /** {@code \mark}; a null label means {@code \default}. */
    record Mark(String label) implements LyMusic {
    }

    /** Free text attached to the timeline ({@code \textMark}, {@code \sectionLabel}). */
    record TextMark(String text) implements LyMusic {
    }

    /** {@code \set Context.property = value}, value reduced to plain text. */
    record SetProperty(String context, String property, String value) implements LyMusic {
    }

    /** {@code \voiceOne} ... {@code \voiceFour}; 0 for {@code \oneVoice}. */
    record VoiceStyle(int voice) implements LyMusic {
    }

    /** {@code \stemUp}/{@code \stemDown} (true/false) or {@code \stemNeutral} (null). */
    record StemDirection(Boolean up) implements LyMusic {
    }

    record ChangeStaff(String staff) implements LyMusic {
    }

    record Cadenza(boolean on) implements LyMusic {
    }

    record Break() implements LyMusic {
    }

    record AutoBeam(boolean on) implements LyMusic {
    }

    record HideNotes(boolean hide) implements LyMusic {
    }

    record Melisma(boolean start) implements LyMusic {
    }

    /** Something with no musical effect. */
    record Empty() implements LyMusic {
    }

    /** A pitch as written: name, alteration and octave marks, not yet resolved. */
    record RawPitch(Step step, int alter, int octaveMarks) {
    }

    /** One pitch of a note or chord. */
    record EventPitch(RawPitch pitch, Integer octaveCheck, boolean forcedAccidental, boolean tie) {
    }

    /** Something attached to an event: {@code c4~}, {@code c4(}, {@code c4\p}, ... */
    sealed interface Post {
    }

    record Tie() implements Post {
    }

    record Slur(boolean start, boolean phrasing) implements Post {
    }

    record Beam(boolean start) implements Post {
    }

    record Artic(Articulation articulation) implements Post {
    }

    record Dynamic(String mark) implements Post {
    }

    record Hairpin(DirectionType.WedgeType type) implements Post {
    }

    record Text(String text, Placement placement, boolean bold, boolean italic) implements Post {
    }

    /** {@code \rest} after a pitch: the event is a rest positioned at that pitch. */
    record PitchedRest() implements Post {
    }
}
