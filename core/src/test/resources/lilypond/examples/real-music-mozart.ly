% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\header {
    title = "Sonata Excerpt"
    composer = "W. A. Mozart"
}

\score {
    \new Staff {
        \tempo 4 = 132
        \time 4/4
        \key c \major
        \clef treble
        \relative c'' {
            c4 e8. g16 c4 g8 e8 |
            d4 f8. a16 d4 a8 f8 |
            e4 g8. b16 e4 b8 g8 |
            c,2 c2 |
        }
    }
}
