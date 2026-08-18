% Source: https://lilypond.org/examples.html
% Licence: GPL/GFDL (see https://lilypond.org/copyright.html)
% Not covered by the MVP: << ... >> polyphonic voices.
\version "2.24.0"

\score {
    \new Staff {
        <<
            { c'4 e'4 g'4 c''4 }
            \\
            { c4 g4 c'4 e'4 }
        >>
    }
}
