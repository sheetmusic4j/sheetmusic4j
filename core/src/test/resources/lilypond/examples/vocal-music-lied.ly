% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\header {
    title = "A Little Lied"
    composer = "Anonymous"
}

\score {
    \new Staff {
        \time 4/4
        \key f \major
        \clef treble
        f'4 g'4 a'4 bes'4 |
        c''4 c''4 bes'4 a'4 |
        g'2 f'2 |
    }
    \addlyrics {
        Sing a lit -- tle song for me now
    }
}
