% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\header {
    title = "Little Prelude"
    composer = "J. S. Bach"
}

\score {
    \new Staff {
        \time 4/4
        \key c \major
        \clef treble
        \relative c'' {
            c8 e g c b d, g b |
            c, e g c b d, g b |
            c,4 e8 g c,4 e8 g |
            c,2 c'2 |
        }
    }
}
