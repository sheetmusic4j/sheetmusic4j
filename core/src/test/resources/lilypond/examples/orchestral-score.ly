% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\header {
    title = "Orchestral Excerpt"
    composer = "Anonymous"
}

\score {
    \new Staff {
        \tempo 4 = 120
        \time 4/4
        \key bes \major
        \clef treble
        bes'8 c''8 d''8 f''8 bes''4 f''4 |
        d''4 bes'4 f'4 bes'4 |
    }
}
