/**
 * Copyright (C) 2010-2015 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser Public License as published by the
 * Free Software Foundation, either version 3.0 of the License, or (at your
 * option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License along
 * with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;
import org.evosuite.Properties;
import org.evosuite.TimeController;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.FitnessReplacementFunction;
import org.evosuite.ga.ReplacementFunction;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of steady state GA
 * 
 * @author Gordon Fraser
 */
public class MonotonicGA<T extends Chromosome> extends GeneticAlgorithm<T> {
	/**
	 *
	 */
	class FitnessRow {
		int iteration;
		double bestFitness;
		double worstFitness;
		int populationSize;

		FitnessRow(int iteration, double bestFitness, double worstFitness, int populationSize) {
			this.iteration = iteration;
			this.bestFitness = bestFitness;
			this.worstFitness = worstFitness;
			this.populationSize = populationSize;
		}
	}

	class ParentToOffspringFitness {
		int iteration;
		boolean wasUsed;
		double parentFitness;
		double offspringFitness;

		ParentToOffspringFitness(int iteration, boolean wasUsed, double parentFitness, double offspringFitness) {
			this.iteration = iteration;
			this.wasUsed = wasUsed;
			this.parentFitness = parentFitness;
			this.offspringFitness = offspringFitness;
		}
	}

	private static final long serialVersionUID = 7846967347821123201L;

	protected ReplacementFunction replacementFunction;

	private final Logger logger = LoggerFactory.getLogger(MonotonicGA.class);

	private final List<FitnessRow> fitnessRows;

	private final List<ParentToOffspringFitness> parentToOffspringFitnesses;

	/**
	 * Constructor
	 * 
	 * @param factory
	 *            a {@link org.evosuite.ga.ChromosomeFactory} object.
	 */
	public MonotonicGA(ChromosomeFactory<T> factory) {
		super(factory);

		setReplacementFunction(new FitnessReplacementFunction());

		fitnessRows = new ArrayList<>(1000);

		parentToOffspringFitnesses = new ArrayList<>(1000);
	}

	/**
	 * <p>
	 * keepOffspring
	 * </p>
	 * 
	 * @param parent1
	 *            a {@link org.evosuite.ga.Chromosome} object.
	 * @param parent2
	 *            a {@link org.evosuite.ga.Chromosome} object.
	 * @param offspring1
	 *            a {@link org.evosuite.ga.Chromosome} object.
	 * @param offspring2
	 *            a {@link org.evosuite.ga.Chromosome} object.
	 * @return a boolean.
	 */
	protected boolean keepOffspring(Chromosome parent1, Chromosome parent2, Chromosome offspring1,
			Chromosome offspring2) {
		return replacementFunction.keepOffspring(parent1, parent2, offspring1, offspring2);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	@Override
	protected void evolve() {
		List<T> newGeneration = new ArrayList<T>();

		// Elitism
		logger.debug("Elitism");
		newGeneration.addAll(elitism());

		// Add random elements
		// new_generation.addAll(randomism());

		while (!isNextPopulationFull(newGeneration) && !isFinished()) {
			logger.debug("Generating offspring");

			T parent1 = selectionFunction.select(population);
			T parent2;
			if (Properties.HEADLESS_CHICKEN_TEST)
				parent2 = newRandomIndividual(); // crossover with new random
													// individual
			else
				parent2 = selectionFunction.select(population); // crossover
																// with existing
																// individual


			T offspring1 = (T) parent1.clone();
			T offspring2 = (T) parent2.clone();

			boolean performedCrossover = false;
			try {
				// Crossover
				if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
					crossoverFunction.crossOver(offspring1, offspring2);
					performedCrossover = true;
				}

			} catch (ConstructionFailedException e) {
				logger.info("CrossOver failed");
				continue;
			}

			// Mutation
			notifyMutation(offspring1);
			offspring1.mutate();
			notifyMutation(offspring2);
			offspring2.mutate();

			if (offspring1.isChanged()) {
				offspring1.updateAge(currentIteration);
			}
			if (offspring2.isChanged()) {
				offspring2.updateAge(currentIteration);
			}

			// The two offspring replace the parents if and only if one of
			// the offspring is not worse than the best parent.
			for (FitnessFunction<T> fitnessFunction : fitnessFunctions) {
				fitnessFunction.getFitness(offspring1);
				notifyEvaluation(offspring1);
				fitnessFunction.getFitness(offspring2);
				notifyEvaluation(offspring2);
			}

			// log parent to offspring fitness
			parentToOffspringFitnesses.add(
					new ParentToOffspringFitness(currentIteration,
							true,
							parent1.getFitness(),
							offspring1.getFitness()));
			parentToOffspringFitnesses.add(
					new ParentToOffspringFitness(currentIteration,
							true,
							parent2.getFitness(),
							offspring2.getFitness()));
			parentToOffspringFitnesses.add(
					new ParentToOffspringFitness(currentIteration,
							performedCrossover,
							parent1.getFitness(),
							offspring2.getFitness()));
			parentToOffspringFitnesses.add(
					new ParentToOffspringFitness(currentIteration,
							performedCrossover,
							parent2.getFitness(),
							offspring1.getFitness()));

			if (keepOffspring(parent1, parent2, offspring1, offspring2)) {
				logger.debug("Keeping offspring");

				// Reject offspring straight away if it's too long
				int rejected = 0;
				if (isTooLong(offspring1) || offspring1.size() == 0) {
					rejected++;
				} else {
					// if(Properties.ADAPTIVE_LOCAL_SEARCH ==
					// AdaptiveLocalSearchTarget.ALL)
					// applyAdaptiveLocalSearch(offspring1);
					newGeneration.add(offspring1);
				}

				if (isTooLong(offspring2) || offspring2.size() == 0) {
					rejected++;
				} else {
					// if(Properties.ADAPTIVE_LOCAL_SEARCH ==
					// AdaptiveLocalSearchTarget.ALL)
					// applyAdaptiveLocalSearch(offspring2);
					newGeneration.add(offspring2);
				}

				if (rejected == 1)
					newGeneration.add(Randomness.choice(parent1, parent2));
				else if (rejected == 2) {
					newGeneration.add(parent1);
					newGeneration.add(parent2);
				}
			} else {
				logger.debug("Keeping parents");
				newGeneration.add(parent1);
				newGeneration.add(parent2);
			}

		}

		population = newGeneration;
		// archive
		updateFitnessFunctionsAndValues();

		currentIteration++;
	}

	private T newRandomIndividual() {
		T randomChromosome = chromosomeFactory.getChromosome();
		for (FitnessFunction<?> fitnessFunction : this.fitnessFunctions) {
			randomChromosome.addFitness(fitnessFunction);
		}
		return randomChromosome;
	}

	/** {@inheritDoc} */
	@Override
	public void initializePopulation() {
		notifySearchStarted();
		currentIteration = 0;

		// Set up initial population
		generateInitialPopulation(Properties.POPULATION);
		logger.debug("Calculating fitness of initial population");
		calculateFitnessAndSortPopulation();

		this.notifyIteration();

		// fitness logging
		fitnessRows.clear();
		fitnessRows.add(new FitnessRow(currentIteration,
				population.get(0).getFitness(),
				population.get(population.size() - 1).getFitness(),
				population.size()));

		parentToOffspringFitnesses.clear();
	}

	private static final double DELTA = 0.000000001; // it seems there is some
														// rounding error in LS,
														// but hard to debug :(

	/** {@inheritDoc} */
	@Override
	public void generateSolution() {
		if (Properties.ENABLE_SECONDARY_OBJECTIVE_AFTER > 0 || Properties.ENABLE_SECONDARY_OBJECTIVE_STARVATION) {
			disableFirstSecondaryCriterion();
		}

		if (population.isEmpty()) {
			initializePopulation();
			assert!population.isEmpty() : "Could not create any test";
		}

		logger.debug("Starting evolution");
		int starvationCounter = 0;
		double bestFitness = Double.MAX_VALUE;
		double lastBestFitness = Double.MAX_VALUE;
		if (getFitnessFunction().isMaximizationFunction()) {
			bestFitness = 0.0;
			lastBestFitness = 0.0;
		}

		while (!isFinished()) {
			
			logger.info("Population size before: " + population.size());
			// related to Properties.ENABLE_SECONDARY_OBJECTIVE_AFTER;
			// check the budget progress and activate a secondary criterion
			// according to the property value.

			{
				double bestFitnessBeforeEvolution = getBestFitness();
				evolve();
				sortPopulation();
				double bestFitnessAfterEvolution = getBestFitness();

				if (getFitnessFunction().isMaximizationFunction())
					assert(bestFitnessAfterEvolution >= (bestFitnessBeforeEvolution
							- DELTA)) : "best fitness before evolve()/sortPopulation() was: " + bestFitnessBeforeEvolution
									+ ", now best fitness is " + bestFitnessAfterEvolution;
				else
					assert(bestFitnessAfterEvolution <= (bestFitnessBeforeEvolution
							+ DELTA)) : "best fitness before evolve()/sortPopulation() was: " + bestFitnessBeforeEvolution
									+ ", now best fitness is " + bestFitnessAfterEvolution;
			}

			{
				double bestFitnessBeforeLocalSearch = getBestFitness();
				applyLocalSearch();
				double bestFitnessAfterLocalSearch = getBestFitness();

				if (getFitnessFunction().isMaximizationFunction())
					assert(bestFitnessAfterLocalSearch >= (bestFitnessBeforeLocalSearch
							- DELTA)) : "best fitness before applyLocalSearch() was: " + bestFitnessBeforeLocalSearch
									+ ", now best fitness is " + bestFitnessAfterLocalSearch;
				else
					assert(bestFitnessAfterLocalSearch <= (bestFitnessBeforeLocalSearch
							+ DELTA)) : "best fitness before applyLocalSearch() was: " + bestFitnessBeforeLocalSearch
									+ ", now best fitness is " + bestFitnessAfterLocalSearch;
			}

			/*
			 * TODO: before explanation: due to static state handling, LS can
			 * worse individuals. so, need to re-sort.
			 * 
			 * now: the system tests that were failing have no static state...
			 * so re-sorting does just hide the problem away, and reduce
			 * performance (likely significantly). it is definitively a bug
			 * somewhere...
			 */
			// sortPopulation();

			double newFitness = getBestFitness();

			if (getFitnessFunction().isMaximizationFunction())
				assert(newFitness >= (bestFitness - DELTA)) : "best fitness was: " + bestFitness
						+ ", now best fitness is " + newFitness;
			else
				assert(newFitness <= (bestFitness + DELTA)) : "best fitness was: " + bestFitness
						+ ", now best fitness is " + newFitness;
			bestFitness = newFitness;

			if (Double.compare(bestFitness, lastBestFitness) == 0) {
				starvationCounter++;
			} else {
				logger.info("reset starvationCounter after " + starvationCounter + " iterations");
				starvationCounter = 0;
				lastBestFitness = bestFitness;

			}

			updateSecondaryCriterion(starvationCounter);

			logger.info("Current iteration: " + currentIteration);
			this.notifyIteration();

			logger.info("Population size: " + population.size());
			logger.info("Best individual has fitness: " + population.get(0).getFitness());
			logger.info("Worst individual has fitness: " + population.get(population.size() - 1).getFitness());

			// log row
			fitnessRows.add(new FitnessRow(currentIteration,
					population.get(0).getFitness(),
					population.get(population.size() - 1).getFitness(),
					population.size()));
		}
		// archive
		TimeController.execute(this::updateBestIndividualFromArchive, "update from archive", 5_000);

		notifySearchFinished();

		{
			// ensure report directory..
			File reportDir = new File(Properties.REPORT_DIR);
			if (!reportDir.exists() && !reportDir.mkdirs()) {
				logger.warn("could not ensure REPORT_DIR at " + Properties.REPORT_DIR);
			}
		}
		{
			// write fitnesses
			String filePath = Properties.REPORT_DIR + File.separator + "fitnesses.csv";
			try (PrintWriter writer = new PrintWriter(filePath, "UTF-8");
				 CSVWriter csvWriter = new CSVWriter(writer, ',')) {
				String[] stringRow = new String[4];

				for (FitnessRow row : fitnessRows) {
					stringRow[0] = Integer.toString(row.iteration);
					stringRow[1] = Double.toString(row.bestFitness);
					stringRow[2] = Double.toString(row.worstFitness);
					stringRow[3] = Integer.toString(row.populationSize);

					csvWriter.writeNext(stringRow);
				}

			} catch (FileNotFoundException e) {
				logger.warn("could not write fitnesses CSV file: " + e.getMessage());
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				logger.warn("could not write fitnesses CSV file: " + e.getMessage());
			} catch (IOException e) {
				logger.warn("could not write fitnesses CSV file: IOException: " + e.getMessage());
			}
		}

		{
			// write parent to offspring fitness values
			// columns will be ITERATION, USED, PARENT_FITNESS, OFFSPRING_FITNESS
			String filePath = Properties.REPORT_DIR + File.separator + "parentToOffspring.csv";
			try (PrintWriter writer = new PrintWriter(filePath, "UTF-8");
				 CSVWriter csvWriter = new CSVWriter(writer, ',')) {
				String[] stringRow = new String[4];

				for (ParentToOffspringFitness row : parentToOffspringFitnesses) {
					stringRow[0] = Integer.toString(row.iteration);
					stringRow[1] = Boolean.toString(row.wasUsed);
					stringRow[2] = Double.toString(row.parentFitness);
					stringRow[3] = Double.toString(row.offspringFitness);

					csvWriter.writeNext(stringRow);
				}

			} catch (FileNotFoundException e) {
				logger.warn("could not write parentToOffspring CSV file: " + e.getMessage());
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				logger.warn("could not write parentToOffspring CSV file: " + e.getMessage());
			} catch (IOException e) {
				logger.warn("could not write parentToOffspring CSV file: IOException: " + e.getMessage());
			}
		}
	}

	private double getBestFitness() {
		T bestIndividual = getBestIndividual();
		for (FitnessFunction<T> ff : fitnessFunctions) {
			ff.getFitness(bestIndividual);
		}
		return bestIndividual.getFitness();
	}

	/**
	 * <p>
	 * setReplacementFunction
	 * </p>
	 * 
	 * @param replacement_function
	 *            a {@link org.evosuite.ga.ReplacementFunction} object.
	 */
	public void setReplacementFunction(ReplacementFunction replacement_function) {
		this.replacementFunction = replacement_function;
	}

	/**
	 * <p>
	 * getReplacementFunction
	 * </p>
	 * 
	 * @return a {@link org.evosuite.ga.ReplacementFunction} object.
	 */
	public ReplacementFunction getReplacementFunction() {
		return replacementFunction;
	}

}
