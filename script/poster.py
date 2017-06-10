#!/usr/bin/env python3

import numpy as np
import evaluate
import matplotlib.pyplot as plt
import math
import argparse


def parse_args():
    parser = argparse.ArgumentParser(description='Drawing for poster of ThumbSense.',
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

if __name__ == '__main__':
    opt = parse_args()
    all = evaluate.readfile(opt)

    for i in range(opt.gesture):
        # ------------- Draw magnitude graph ------------- #
        data = np.array([ math.sqrt(x*x + y*y + z*z) for (l, x, y, z) in all[i][0][0] ])
        plt.figure()
        plt.plot(range(len(data)), data)
        data = np.array([ math.sqrt(x*x + y*y + z*z) for (l, x, y, z) in all[i][0][1] ])
        plt.plot(range(len(data)), data)
        # plt.bar(range(len(data)), data, color='rgb') # or `color=['r', 'g', 'b']`
        # plt.show()
        plt.xlabel('frame')
        plt.ylabel('magnitude')
        plt.savefig('gesture_%d_time.png' % i, dpi=1200)

        # ------------- Draw FFT graph ------------------- #
        data = evaluate.FFT(opt, all[i][0][0])
        plt.figure()
        plt.plot(range(len(data[0])), data[0])
        data = evaluate.FFT(opt, all[i][0][1])
        plt.plot(range(len(data[0])), data[0])

        plt.xlabel('Hz')
        plt.ylabel('magnitude')
        plt.savefig('gesture_%d_fft.png' % i, dpi=1200)

