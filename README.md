# Java and OpenGL iFFT Ocean Simulation
This was a research project for my diploma thesis at the HTL in St.Pölten, Lower Austria.
An algorithm developed by Jerry Tessendorf implemented and ported to OpenGL, GLSL and Java to simulate photorealistic oceans.

## Rendering Pipeline
The following rendering pipeline was implemented in GLSL to create the normals- and folding-map from a phillips-spectrum using the inverse fast fourier transformation.

![Simulation Pipeline](https://github.com/danielkleebinder/ocean-simulation/blob/master/imgs/Simulation.png?raw=true)

## Results
The simulation is capable of applying different styles to the oceans surface and behaviour.

![Dark Ocean](https://github.com/danielkleebinder/ocean-simulation/blob/master/imgs/Result.jpg?raw=true)
![Bright_Ocean](https://github.com/danielkleebinder/ocean-simulation/blob/master/imgs/Result2.jpg?raw=true)
