package com.test;

import com.google.ortools.Loader;
import com.google.ortools.init.OrToolsVersion;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

/** Minimal Linear Programming example to showcase calling the solver. */
public final class BasicExample {
    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        System.out.println("Google OR-Tools version: " + OrToolsVersion.getVersionString());

        // Create the linear solver with the GLOP backend.
        MPSolver solver = MPSolver.createSolver("GLOP");
        if (solver == null) {
            System.out.println("Could not create solver GLOP");
            return;
        }

        // Create the variables x and y.
        MPVariable x = solver.makeNumVar(0.0, 1.0, "x");
        MPVariable y = solver.makeNumVar(0.0, 2.0, "y");

        System.out.println("Number of variables = " + solver.numVariables());

        double infinity = Double.POSITIVE_INFINITY;
        // Create a linear constraint, x + y <= 2.
        MPConstraint ct = solver.makeConstraint(-infinity, 2.0, "ct");
        ct.setCoefficient(x, 1);
        ct.setCoefficient(y, 1);

        System.out.println("Number of constraints = " + solver.numConstraints());

        // Create the objective function, 3 * x + y.
        MPObjective objective = solver.objective();
        objective.setCoefficient(x, 3);
        objective.setCoefficient(y, 1);
        objective.setMaximization();

        System.out.println("Solving with " + solver.solverVersion());
        final MPSolver.ResultStatus resultStatus = solver.solve();

        System.out.println("Status: " + resultStatus);
        if (resultStatus != MPSolver.ResultStatus.OPTIMAL) {
            System.out.println("The problem does not have an optimal solution!");
            if (resultStatus == MPSolver.ResultStatus.FEASIBLE) {
                System.out.println("A potentially suboptimal solution was found");
            } else {
                System.out.println("The solver could not solve the problem.");
                return;
            }
        }

        System.out.println("Solution:");
        System.out.println("Objective value = " + objective.value());
        System.out.println("x = " + x.solutionValue());
        System.out.println("y = " + y.solutionValue());

        System.out.println("Advanced usage:");
        System.out.println("Problem solved in " + solver.wallTime() + " milliseconds");
        System.out.println("Problem solved in " + solver.iterations() + " iterations");
    }

    private BasicExample() {}
}