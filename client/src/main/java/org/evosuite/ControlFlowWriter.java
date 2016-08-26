package org.evosuite;

import org.evosuite.graphs.EvoSuiteGraph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Using -Dwrite_cfg=true may lead to SecurityExceptions due to sandboxing,
 * when the GraphPool tries to write graphs.
 * Therefore, another Thread with the right permissions takes graphs
 * and writes them.
 *
 * Created by bgraf on 26.08.16.
 */
public class ControlFlowWriter implements Runnable {
	private final Logger logger = LoggerFactory.getLogger(ControlFlowWriter.class);

	private BlockingQueue<EvoSuiteGraph> queue;

	private ControlFlowWriter() {
		queue = new ArrayBlockingQueue<EvoSuiteGraph>(100);
	}

	@Override
	public void run() {
		EvoSuiteGraph graph;

		while (true) {
			try {
				graph = queue.take();
				logger.debug("Writing graph: " + graph.getName());
				graph.toDot();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private  static ControlFlowWriter instance = null;

	public static void writeGraph(EvoSuiteGraph graph) {
		try {
			getInstance().queue.put(graph);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static ControlFlowWriter getInstance() {
		if (instance == null) {
			instance = new ControlFlowWriter();
			Thread thr = new Thread(instance);
			thr.setDaemon(true);
			thr.start();
		}
		return instance;
	}
}
