Red/black Successive Over Relaxation (SOR) is an iterative method for solving
discretized Laplace equations on a grid.
This implementation is an Ibis version. It distributes the grid row-wise among
the CPUs. Each CPU exchanges one row of the matrix with its neighbours at the
beginning of each iteration.

The program options are: <NROW> <NITERATIONS>
where
    <NROW> is the number of rows/columns in the array, and
    <NITERATIONS> is the number of iterations (when set to zero, the
	number of iterations is determined dynamically, using a threshold for
	the sum of the differences).
