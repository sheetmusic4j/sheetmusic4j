% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
\version "2.24.0"

\header {
    title = "Simple Choral"
}

\score {
    \new Staff {
        \time 4/4
        \key d \major
        \clef treble
        d'4 fis'4 a'4 d''4 |
        cis''4 a'4 fis'4 d'4 |
        e'4 g'4 b'4 e''4 |
        d''4. cis''8 d''2 |
    }
    \addlyrics {
        Al -- le -- lu -- ia,
        al -- le -- lu -- ia,
        praise __ the Lord!
    }
}
