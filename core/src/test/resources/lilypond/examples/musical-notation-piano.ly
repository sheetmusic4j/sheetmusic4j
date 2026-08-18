% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\score {
    \new Staff {
        \time 3/4
        \key g \major
        \clef treble
        \relative c' {
            g'8 a8 b8 c8 d4 |
            e4. d8 c4 |
            b8 a8 g4 r4 |
        }
    }
}
