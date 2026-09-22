# Design notes — V1

## What the app is

A drawer of everyday instruments. Each one is an *object* with its own material - wood, brushed
aluminium, glass, paper, mahogany - and each is operated the way the object is: a stopwatch has
a crown, an hourglass is turned over, a metronome's weight slides along its rod.

## Information architecture

Families are named by what they measure. V1 ships four, holding six instruments:

| Family   | Instruments                     | Why grouped                                  |
|----------|---------------------------------|----------------------------------------------|
| TIME     | Stopwatch · Pomodoro · Hourglass | count up, count in cycles, count down        |
| RHYTHM   | Metronome                       | time you play along to                        |
| CALENDAR | Calendar (month / year)         | time you look up                              |
| HABITS   | Habits                          | time you keep                                 |

Deferred to V1.1: **LIFE** (age in numbers, a life grid, since/until - the Age in Motion widget
already has the engine) and **COUNT** (a mechanical tally counter). The calendar's day details
already cover the simplest "since/until" need: tap any day to see how many days away it is.

**Cut:** a separate "Timer" instrument. The Pomodoro devices run a single session when the set
is 1, and the hourglass is a countdown with presets - a third countdown would just repeat them.

## Navigation

A floating bar at the bottom holds the families; it scrolls sideways if more are added. Tapping a
family with several instruments slides a second pill up out of it - the drawer opening; a family
with one instrument opens straight away. The red dot on the left is home. Back closes the drawer,
then returns home.

## Coherence

The instruments look nothing alike on purpose. What ties them together is the frame: every
screen has the same small spaced capitals at the top, the same row of design chips under it, and
the same black floating bar at the bottom. Sound follows the material (plastic keys tock, the
aluminium Slab clicks, the Pocket's membrane thumps, dials tick in detents) and every press also
buzzes.

## Designs per instrument

- **Stopwatch** - Black or white dial. No on-screen buttons: the crown starts and stops it, the side
  pusher takes laps or resets. The dial is sized to fit any screen, and its static layers are drawn
  once and cached, so the hand sweeps at the display's full rate.
- **Pomodoro** - three pieces of hardware: *Module* (white and orange, pixel LCD, ribbed wheel),
  *Slab* (brushed aluminium, red Play, knurled crown, cycle read-out) and *Pocket* (green LCD with a
  sleeping cat and a speaker grille that ripples while it runs). Focus, break and session presets
  sit underneath.
- **Hourglass** - *Classic* (turned wood), *Dusk* (flat illustration), *Prism* (iridescent glass),
  *Dot* (Nothing dot matrix). Each has a split-flap clock in its own materials. Pick a time and the
  glass turns itself over; tap to pause; hold a preset to change it. Sand levels follow volume,
  not height, so it drains like a real glass.
- **Metronome** - *Studio* (desk unit: LCD, rotary dial with detents, tap button, beat lights) and
  *Pendulum* (mahogany with a sliding brass weight). Clicks are placed by sample on an audio thread,
  so the beat cannot drift; the visuals follow the speaker, not a timer.
- **Calendar** - *Glass* (a frosted visionOS-style panel over a slowly drifting room) and *Paper*
  (a coral header with the date large). Month and year views; no events, no sync.
- **Habits** - one daily tap per habit, streaks, a 20-week dot board, and a page per habit where
  past days can be filled in.
