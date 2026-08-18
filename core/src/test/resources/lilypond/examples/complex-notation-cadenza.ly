% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\score {
    \new Staff {
        \time 4/4
        \key c \major
        \clef treble
        \tuplet 3/2 { c'8 d'8 e'8 }
        \tuplet 3/2 { f'8 g'8 a'8 }
        \tuplet 5/4 { b'16 c''16 d''16 e''16 f''16 } |
        g''2 c''2 |
    }
}
