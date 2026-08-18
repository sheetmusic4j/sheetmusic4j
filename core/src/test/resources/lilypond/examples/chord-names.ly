% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\score {
    \new Staff {
        \time 4/4
        \key c \major
        \clef treble
        c'4 e'4 g'4 c''4 |
        a'4 e'4 c'4 g4 |
    }
    \chordmode {
        c1 | a1:m
    }
}
