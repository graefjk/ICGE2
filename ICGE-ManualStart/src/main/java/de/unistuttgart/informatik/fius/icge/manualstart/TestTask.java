/*
 * This source file is part of the FIUS ICGE project.
 * For more information see github.com/FIUS/ICGE2
 * 
 * Copyright (c) 2019 the ICGE project authors.
 * 
 * This software is available under the MIT license.
 * SPDX-License-Identifier:    MIT
 */
package de.unistuttgart.informatik.fius.icge.manualstart;

import de.unistuttgart.informatik.fius.icge.simulation.Position;
import de.unistuttgart.informatik.fius.icge.simulation.Simulation;
import de.unistuttgart.informatik.fius.icge.simulation.tasks.Task;


/**
 * Test task for manual start.
 */
public class TestTask implements Task {
    
    @Override
    public void run(final Simulation sim) {
        final TestEntity tE = new TestEntity();
        sim.getPlayfield().addEntity(new Position(3, 4), tE);
        try {
            while (true) {
                tE.move();
                tE.move();
                tE.turnClockWise();
            }
        } catch (final Exception e) {
        }
    }
}
