package jcog.learn.ntm.learn;

import jcog.learn.ntm.NTM;

import java.util.ArrayList;
import java.util.List;

public interface INTMTeacher {

    default List<double[]> trainAndGetOutput(double[][] input, double[][] knownOutput) {

        NTM[] machines = trainInternal(input, knownOutput);
        return getMachineOutputs(machines);

    }

    default NTM[] train(double[][] input, double[][] knownOutput) {
        return trainInternal(input, knownOutput);
    }

    NTM[] trainInternal(double[][] input, double[][] knownOutput);

    static List<double[]> getMachineOutputs(NTM[] machines) {
        List<double[]> realOutputs = new ArrayList<>(machines.length);
        for (NTM machine : machines) {
            realOutputs.add(machine.getOutput());
        }
        return realOutputs;
    }
}


