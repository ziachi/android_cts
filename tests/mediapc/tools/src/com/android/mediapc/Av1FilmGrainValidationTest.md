## Av1FilmGrainValidationTest

The test uses dpov_1920x1080_60fps_av1_10bit_film_grain.mp4 video clip, which is included in
[CtsMediaPerformanceClassTestCases-3.3.zip](https://dl.google.com/android/xts/cts/tests/mediapc/CtsMediaPerformanceClassTestCases-3.3.zip).

### Data Generation Process
 * An AV1 clip is created with film-grain enabled using reference encoder. Sample encoding recipe is shared below.

```sh
# Encode video with film-grain using reference encoder (SvtAv1EncApp)
$ SvtAv1EncApp -b <output.ivf> -i <input.yuv> -w <int> -h <int> --fps 30 --rc 2 --tbr 1M \
  --svtav1-params film-grain=40:tile-rows=0:tile-columns=0
```

 * The clip is decoded twice using reference decoder: once with film-grain enabled and once with film-grain disabled. For 10-bit clips, if the decoded yuv format is yuv420p10le this needs to be converted to p010 as the VarianceMain util expects input to be in p010 format.

```sh
# Decode encoded video with film-grain enabled using reference decoder (SvtAv1DecApp)
$ SvtAv1DecApp -i <output.ivf> -o <with-film-grain.yuv>

# Decode encoded video with film-grain disabled using reference decoder (SvtAv1DecApp)
$ SvtAv1DecApp -i <output.ivf> -skip-film-grain -o <without-film-grain.yuv>
```

 * The variance of all frames for both decoded clips is calculated.

```sh
# Compute per frame variance for the decoded yuvs and write to txt file
# The txt files contain frame index and variance for all the frames.
$ VarianceMain -f fg.yuv -o variance_fgyes.txt -w 1920 -h 1080 -b 10
$ VarianceMain -f nofg.yuv -o variance_fgno.txt -w 1920 -h 1080 -b 10
```

 * Frames with a significant difference in variance (based on a threshold) are selected.
 * The variance values for these selected frames are used in the Av1FilmGrainValidationTest.
