This directory contains a simple implementation of cellular automata (CA).
It uses the Ibis communication classes immediately.

It is currently hard-coded to implement Conway's game of Life (which is
a particular CA).

There are two versions of the program, Cell1D is closed-world, and OpenCell1D
is open-world.

The program parameters are:
-size <size>
    the width/height of the board (default is 3000)
<ngenerations>
    the number of generations to run this simulation (default is 30)
