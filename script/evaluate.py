#!/usr/bin/env python3

from __future__ import print_function

import argparse
from numpy.fft import fft
import numpy as np
from scipy import signal
from sklearn import svm
from collections import Counter


def parse_args():
    parser = argparse.ArgumentParser(description='Evaluator of ThumbSense project.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('-i', '--input', required=True, 
                        help='Input file for evaluation.')
    parser.add_argument('-g', '--gesture', type=int, default=50,
                        help='Number of gesture been experimented.')
    parser.add_argument('-t', '--trial', type=int, default=10,
                        help='Number of trial been experimented.')
    parser.add_argument('-f', '--frame-size', type=int, default=32,
                        help='FFT frame size.')
    parser.add_argument('-w', '--window-size', type=int, default=3,
                        help='Window size of FFT spectra.')

    return parser.parse_args()

def readfile(opt):
    ans = [ [ [ [], [] ] for i in range(opt.trial) ] for i in range(opt.gesture) ]
    with open(opt.input, 'r') as file:
        for line in file.readlines():
            gest, id, sensor, ts, x, y, z = line[:-1].split(',')
            ans[int(gest)][int(id) - 1][ int(sensor) // 4 ].append(
                (int(ts), float(x), float(y), float(z.split()[0])))

    return ans

def FFT(opt, trial):
    """
    Transferring each trial into FFT as ViBand designed.
    """
    ans = []
    samples = len(trial)

    # 1. Sort by timestamp
    trial.sort(key=lambda x: x[0])

    # 2. FFT x Hamming Window
    hamming = np.hamming(opt.frame_size)

    for i in range(samples // opt.frame_size):
        start = i * opt.frame_size
        end = start + opt.frame_size
        if end > samples:
            end = samples
        
        def toSpectra(id):
            return np.multiply(
                fft(np.array(list(map(lambda x: x[id], trial[start:end])))), 
                hamming)

        X = toSpectra(1)
        Y = toSpectra(2)
        Z = toSpectra(3)

        # 3. Get element-wise maximum & Remove DC Component
        frame = np.maximum(np.maximum(X, Y), Z)
        frame = np.delete(frame, 0)

        ans.append(frame)

    return ans

def preprocess(opt, data):
    """
    Preprocessing for input data. 
    Then transferred by FFT and apply Hamming window.
    """
    for gesture in range(opt.gesture):
        trials = data[gesture]
        for trial in range(opt.trial):
            trials[trial][0] = FFT(opt, trials[trial][0])
            trials[trial][1] = FFT(opt, trials[trial][1])

def toFeature(opt, spectra):
    # print ('Spectra length:', len(spectra))
    features = []
    for start in range(len(spectra) - opt.window_size + 1):
        S = np.mean(spectra[start:start + opt.window_size], axis=0)
        stat = np.array([
            np.mean(S), np.std(S), np.sum(S),
            np.max(S), np.min(S), np.median(S),
        ])
        # TODO: the widths range is not sure...
        peaks = np.take(np.concatenate((signal.find_peaks_cwt(S, np.arange(1, 5)),
                                        np.full(5, -1))), # Make sure >= 5
                        range(5)) # Get 5 peaks # TODO: get the top 5!!

        band = np.concatenate([
            np.divide(np.roll(S, shift), S) for shift in range(1, len(S))])

        features.append(np.concatenate((S, stat, peaks, np.diff(S), band)))

    return features


def evaluate(opt, data):
    # ----------- Build data ---------- #
    for gesture in range(opt.gesture):
        trials = data[gesture]
        for trial in range(opt.trial):
            accel = toFeature(opt, trials[trial][0])
            gyro = toFeature(opt, trials[trial][1])
            # print ('Accel', accel)
            # print ('Gyro', gyro)

            xs = [ np.concatenate((accel[i], gyro[i])) for i in range(len(accel)) ]
            trials[trial] = xs

    # TODO: Normalization!!!!!

    # ----------- Takeout 1 each time ------------ #
    for takeout in range(opt.trial):
        print ("----------------------------------")
        print ('Now takeout trial %d.' % (takeout))
        train_x = []
        train_y = []
        test_x = []
        test_y = []

        for gesture in range(opt.gesture):
            trials = data[gesture]
            for trial in range(opt.trial):
                xs = trials[trial]
                # print ("Trial: %d .... len: %d" % (trial, len(xs)), end='')
                
                if trial == takeout:
                    # print ("... takeout! gesture:", gesture)
                    test_x += xs
                    test_y += ([ gesture ] * len(xs))
                else:
                    # print ("... x")
                    train_x += xs
                    train_y += ([ gesture ] * len(xs))

        # ---------- Run LibSVM ----------- #
        model = svm.SVC()
        model.fit(train_x, train_y)
        predicted = model.predict(test_x)
        
        count = [[]] * opt.gesture
        precision = 0
        for guess, ans in zip(predicted, test_y):
            count[ans].append(guess)

        print ("Predict\t", " ".join(map(lambda x: "{:>2}".format(x), predicted)))
        print ("Answer\t", " ".join(map(lambda x: "{:>2}".format(x), test_y)))

        for gesture, guesses in enumerate(count):
            max_guess, num = Counter(guesses).most_common(1)[0]
            print ("%d,%d (%d/%d)" % (gesture, max_guess, num, len(guesses)))
            if max_guess == gesture:
                precision += 1

        print ("Precision:", float(precision) / opt.gesture)


if __name__ == '__main__':
    opt = parse_args()
    data = readfile(opt)
    preprocess(opt, data)
    evaluate(opt, data)
