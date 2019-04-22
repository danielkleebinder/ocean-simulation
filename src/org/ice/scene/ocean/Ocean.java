package org.ice.scene.ocean;

import java.nio.FloatBuffer;
import java.util.logging.Logger;
import org.ice.core.AbstractEngine;
import org.ice.core.AbstractEngineState;
import org.ice.math.FastMath;
import org.ice.math.FastRandom;
import org.ice.math.Vector2f;
import org.ice.math.Vector3f;
import org.ice.math.Vector4f;
import org.ice.math.paint.ColorRGBA;
import org.ice.physics.CollisionShape;
import org.ice.physics.PhysicsSystem;
import org.ice.physics.boundings.BoundingSphere;
import org.ice.platform.BufferGroup;
import org.ice.platform.GeometryBuffer;
import org.ice.platform.Pipeline;
import org.ice.platform.VertexArrayBuffer;
import org.ice.platform.texture.ImageData;
import org.ice.platform.texture.ImageTexture2D;
import org.ice.platform.texture.Texture;
import org.ice.platform.texture.Texture2D;
import org.ice.platform.texture.TextureCubeMap;
import org.ice.scene.Mesh;
import org.ice.scene.lod.TessellationLevelOfDetail;
import org.ice.scene.option.DataModificationType;
import org.ice.scene.option.PrimitiveType;
import org.ice.scene.render.Renderer;
import org.ice.shader.ComputeShader;
import org.ice.shader.FragmentShader;
import org.ice.shader.ShaderProgram;
import org.ice.shader.TessellationControlShader;
import org.ice.shader.TessellationEvaluationShader;
import org.ice.shader.VertexShader;
import org.ice.shader.parameter.BoolParameter;
import org.ice.shader.parameter.ColorRGBAParameter;
import org.ice.shader.parameter.FloatParameter;
import org.ice.shader.parameter.Image2DParameter;
import org.ice.shader.parameter.Mat3Parameter;
import org.ice.shader.parameter.Mat4Parameter;
import org.ice.shader.parameter.Sampler2DParameter;
import org.ice.shader.parameter.Vec2Parameter;
import org.ice.shader.parameter.Vec3Parameter;
import org.ice.util.AssetManager;
import org.ice.util.BufferUtilities;
import org.ice.util.EngineTimer;
import org.ice.util.GraphicsUtilities;
import org.ice.util.LoggerFactory;
import org.ice.util.OffscreenSampler;

/**
 *
 * @author Daniel Kleebinder
 * @since 1.0.0
 */
public class Ocean extends Mesh {

	/**
	 * Class logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(Ocean.class);
	/**
	 * Natural phillips spectrum algorithm.
	 */
	public static final SpectrumAlgorithm PHILLIPS = (Ocean water, Vector2f k) -> {
		float r = (water.getWindSpeed() * water.getWindSpeed()) / (water.getGravity() * 0.01f);
		float l = r / 1_000.0f;

		float sqrK = k.x * k.x + k.y * k.y;
		float cosK = k.x * water.getWindDirection().x + k.y * water.getWindDirection().y;

		float phillips = water.getAmplitude() * FastMath.exp(-1.0f / (sqrK * r * r)) / (sqrK * sqrK * sqrK) * (cosK * cosK);

		if (cosK < 0.0f) {
			phillips *= 0.07f;
		}

		return phillips * FastMath.exp(-sqrK * l * l);
	};

	//Surface
	/**
	 * Surface shader.
	 */
	private ShaderProgram surfaceShader;

	//Spectrum Displacement
	/**
	 * Spectrum shader.
	 */
	private ShaderProgram spectrumShader;

	//Fast Fourier Transformation
	private ShaderProgram fftShader;
	/**
	 * Shader for calculating the normals and the folding value.
	 */
	private ShaderProgram nfShader;

	//Fast Fourier Transformation
	/**
	 * Butterfly dimension.
	 */
	private int dimension = 512;
	/**
	 * Number of butterflies.
	 */
	private int butterflies = 9;

	//Textures
	/**
	 * Spectrum texture.
	 */
	public Texture2D spectrum;
	/**
	 * Omega texture.
	 */
	private Texture2D omega;
	/**
	 * Butterfly texture.
	 */
	private Texture2D butterfly;
	/**
	 * Heightfield values x, y and z.
	 */
	public ImageTexture2D heightfieldX, heightfieldY, heightfieldZ;
	public Texture2D[] samplerTextures = new Texture2D[6];
	private OffscreenSampler[] samplers = new OffscreenSampler[2];
	/**
	 * Resulting time domain spectrum texture.
	 */
	public ImageTexture2D result;
	/**
	 * Contains the normals and the folding value.
	 */
	public ImageTexture2D nfMap;

	//Properties
	/**
	 * Spectrum algorithm.
	 */
	private SpectrumAlgorithm spectrumAlgorithm = PHILLIPS;
	/**
	 * Level of detail.
	 */
	private TessellationLevelOfDetail levelOfDetail = new TessellationLevelOfDetail();

	/**
	 * Specular intensity.
	 */
	private float specularIntensity = 200.0f;
	/**
	 * Gravity.
	 */
	private float gravity = PhysicsSystem.GRAVITY_DEFAULT;
	/**
	 * Patch size.
	 */
	private float patchSize = 256.0f;
	/**
	 * Wind speed.
	 */
	private float windSpeed = 4.0f;
	/**
	 * Wave amplitude.
	 */
	private float amplitude = 1.4f;
	/**
	 * Reflection factor.
	 */
	private float reflection = 0.2f;
	/**
	 * Foam factor.
	 */
	private float foam = 0.8f;
	/**
	 * Foam height modification.
	 */
	private float foamHeightModification = 1.4f;
	/**
	 * Perlin Noise height scale.
	 */
	private float perlinNoiseHeight = 17.5f;
	/**
	 * Perlin Noise animation speed.
	 */
	private float perlinNoiseAnimationSpeed = 5.0f;
	/**
	 * Surface transparency.
	 */
	private float transparency = 0.95f;
	/**
	 * Wind direction.
	 */
	private Vector2f windDirection = new Vector2f(0.35f, 0.65f);
	/**
	 * Perlin Noise height map scale.
	 */
	private Vector2f perlinNoiseScale = new Vector2f(40.0f, 40.0f);
	/**
	 * Choppy scale.
	 */
	private Vector2f choppyScale = new Vector2f(1.5f, 1.5f);
	/**
	 * Scale color.
	 */
	private ColorRGBA diffuseColor = new ColorRGBA(1.0f, 1.0f, 1.0f);
	/**
	 * Color of the waves at the top.
	 */
	private ColorRGBA waterColor = new ColorRGBA(30, 60, 60, 200);
	/**
	 * Color of the waves at the bottom
	 */
	private ColorRGBA deepWaterColor = new ColorRGBA(20, 40, 50, 255);
	/**
	 * Foam map.
	 */
	private Texture2D foammap;
	/**
	 * Fresnel normals map.
	 */
	private Texture2D fresnelmap;
	/**
	 * Sky texture.
	 */
	private TextureCubeMap sky;
	/**
	 * If some property has changed.
	 */
	private boolean hasChanged = true;
	/**
	 * Camera position.
	 */
	private Vector3f cameraPosition = Vector3f.ZERO;
	/**
	 * Grid diviation.
	 */
	private float diviation = 8.0f;
//	private float diviation = 18.0f;

	/**
	 * Creates a new water surface which is hardware accelerated.
	 */
	public Ocean() {
		//Setup parameters
		foammap = AssetManager.loadTextureSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/materials/water/Foam.jpg"));
		foammap.setWrapMode(Texture.WrapAxis.S, Texture.WrapMode.REPEAT);
		foammap.setWrapMode(Texture.WrapAxis.T, Texture.WrapMode.REPEAT);

		fresnelmap = AssetManager.loadTextureSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/materials/water/Normalmap.jpg"));
		fresnelmap.setWrapMode(Texture.WrapAxis.S, Texture.WrapMode.REPEAT);
		fresnelmap.setWrapMode(Texture.WrapAxis.T, Texture.WrapMode.REPEAT);

		Texture2D skyTexture = AssetManager.loadTextureSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/materials/water/ClearSky.jpg"));
		sky = new TextureCubeMap(skyTexture, skyTexture, skyTexture, skyTexture, skyTexture, skyTexture);

		//Setup all shaders
		surfaceShader = new ShaderProgram("Surface Shader");
		spectrumShader = new ShaderProgram("Spectrum Shader");
		fftShader = new ShaderProgram("Fast Fourier Transformation Shader");
		nfShader = new ShaderProgram("Normals Shader");

		surfaceShader.getShaders().add(new VertexShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/Water.vs"))));
		surfaceShader.getShaders().add(new TessellationControlShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/Water.tcs"))));
		surfaceShader.getShaders().add(new TessellationEvaluationShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/Water.tes"))));
		surfaceShader.getShaders().add(new FragmentShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/Water.fs"))));

		spectrumShader.getShaders().add(new ComputeShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/SpectrumDisplacement.comp"))));

		fftShader.getShaders().add(new VertexShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/Processing.vs"))));
		fftShader.getShaders().add(new FragmentShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/TwoDimensionalFFT.fs"))));

		nfShader.getShaders().add(new ComputeShader(AssetManager.loadTextSuppressed(Ocean.class.getResourceAsStream("/org/ice/assets/shaders/ocean/Normals.comp"))));

		surfaceShader.setAutomaticUniformMatrices(false);
		spectrumShader.setAutomaticUniformMatrices(false);
		nfShader.setAutomaticUniformMatrices(false);

		surfaceShader.compile();
		spectrumShader.compile();
		fftShader.compile();
		nfShader.compile();

		((AbstractEngineState) AbstractEngine.getContext()).getRenderer().getListeners().add((Renderer.Listener) (Renderer.RenderingState state) -> {
			if (!Renderer.RenderingState.POST_PERSPECTIVE_RENDERING.equals(state)) {
				return;
			}
			GraphicsUtilities.begin2DRendering();

			Pipeline.setMatrixMode(Pipeline.Mode.PROJECTION);
			Pipeline.push();
			GraphicsUtilities.toOrthographic(0, 0, dimension, dimension);
			Pipeline.setMatrixMode(Pipeline.Mode.MODEL);

			// begin sampling
			Sampler2DParameter[] samplerParametersX = new Sampler2DParameter[2];
			samplerParametersX[0] = new Sampler2DParameter("m_HeightFieldX", samplerTextures[0]);
			samplerParametersX[1] = new Sampler2DParameter("m_HeightFieldX", samplerTextures[1]);
			Sampler2DParameter[] samplerParametersY = new Sampler2DParameter[2];
			samplerParametersY[0] = new Sampler2DParameter("m_HeightFieldY", samplerTextures[2]);
			samplerParametersY[1] = new Sampler2DParameter("m_HeightFieldY", samplerTextures[3]);
			Sampler2DParameter[] samplerParametersZ = new Sampler2DParameter[2];
			samplerParametersZ[0] = new Sampler2DParameter("m_HeightFieldZ", samplerTextures[4]);
			samplerParametersZ[1] = new Sampler2DParameter("m_HeightFieldZ", samplerTextures[5]);

			Sampler2DParameter bufferflyTextureParameter = new Sampler2DParameter("m_ButterflyTexture", butterfly);

			for (int i = 0; i < butterflies * 2; i++) {
				if (i < butterflies) {
					fftShader.getParameters().clear();
					if (i == 0) {
						fftShader.getParameters().add(new Sampler2DParameter("m_HeightFieldX", heightfieldX));
						fftShader.getParameters().add(new Sampler2DParameter("m_HeightFieldY", heightfieldY));
						fftShader.getParameters().add(new Sampler2DParameter("m_HeightFieldZ", heightfieldZ));
					} else {
						fftShader.getParameters().add(samplerParametersX[i % 2]);
						fftShader.getParameters().add(samplerParametersY[i % 2]);
						fftShader.getParameters().add(samplerParametersZ[i % 2]);
					}
					fftShader.getParameters().add(bufferflyTextureParameter);
					fftShader.getParameters().add(new FloatParameter("m_Dimension", (float) dimension));
					fftShader.getParameters().add(new FloatParameter("m_PatchSize", patchSize));
					fftShader.getParameters().add(new BoolParameter("m_Vertical", false));
					fftShader.getParameters().add(new BoolParameter("m_LastPass", false));
					fftShader.getParameters().add(new FloatParameter("m_ButterflyIndex", i / (float) (butterflies - 1)));
					samplers[(i + 1) % 2].sample(fftShader, false);
				} else {
					fftShader.getParameters().clear();
					fftShader.getParameters().add(samplerParametersX[i % 2]);
					fftShader.getParameters().add(samplerParametersY[i % 2]);
					fftShader.getParameters().add(samplerParametersZ[i % 2]);
					fftShader.getParameters().add(bufferflyTextureParameter);
					fftShader.getParameters().add(new FloatParameter("m_Dimension", (float) dimension));
					fftShader.getParameters().add(new FloatParameter("m_PatchSize", patchSize));
					fftShader.getParameters().add(new BoolParameter("m_Vertical", true));
					fftShader.getParameters().add(new BoolParameter("m_LastPass", (i == (butterflies * 2 - 1))));
					fftShader.getParameters().add(new FloatParameter("m_ButterflyIndex", (i - butterflies) / (float) (butterflies - 1)));
					samplers[(i + 1) % 2].sample(fftShader, false);
				}
			}

			ShaderProgram.unuseAllShaders();

			// end sampling
			Pipeline.setMatrixMode(Pipeline.Mode.PROJECTION);
			Pipeline.pop();

			GraphicsUtilities.end2DRendering();
		});
	}

	public void setSurfaceShader(ShaderProgram surfaceShader) {
		this.surfaceShader = surfaceShader;
	}

	public ShaderProgram getSurfaceShader() {
		return surfaceShader;
	}

	@Override
	public void optimizeBounding() {
		bounding = new BoundingSphere(10_000.0f);
	}

	@Override
	public CollisionShape createCollisionShape() {
		throw new UnsupportedOperationException("Collision with ocean is not possible!");
	}

	/**
	 * Requests that the spectrum, the omega and the butterfly textures should
	 * be rebuild.
	 * <br>
	 * Those textures are all pre-calculated. The pre-calculated textures<br>
	 * will be rebuild in the next render pass.
	 */
	public void requestSpectrumRebuild() {
		hasChanged = true;
	}

	@Override
	public void compile(VertexArrayBuffer vab) {
		vao = vab;

		long s = System.currentTimeMillis();

		int width = 128;
		int height = 128;

		// (float) FastMath.log2((width * height) / 2.0f);
//		float diviation = 8;
		BufferGroup group = new BufferGroup(PrimitiveType.PATCHES);

		GeometryBuffer<Vector3f> vs = new GeometryBuffer<>(DataModificationType.STATIC_DRAW);
		GeometryBuffer<Vector3f> ns = new GeometryBuffer<>(DataModificationType.STATIC_DRAW);
		GeometryBuffer<Vector4f> ts = new GeometryBuffer<>(DataModificationType.STATIC_DRAW);
		GeometryBuffer<ColorRGBA> cs = new GeometryBuffer<>(DataModificationType.STATIC_DRAW);

		vs.setDataAttributePosition(VertexArrayBuffer.ATTRIB_LOCATION_VERTEX);
		ns.setDataAttributePosition(VertexArrayBuffer.ATTRIB_LOCATION_NORMAL);
		ts.setDataAttributePosition(VertexArrayBuffer.ATTRIB_LOCATION_TEXCOORD);
		cs.setDataAttributePosition(VertexArrayBuffer.ATTRIB_LOCATION_COLOR);

		group.getBuffers().add(vs);
		group.getBuffers().add(ns);
		group.getBuffers().add(ts);
		group.getBuffers().add(cs);

		for (int z = 0; z < width; z++) {
			for (int x = 0; x < height; x++) {
				float y = 0.0f;

				float f = 2.0f;
				Vector3f v0 = new Vector3f(x, y, z).multLocal(f);
				Vector3f v1 = new Vector3f(x, y, z + 1).multLocal(f);
				Vector3f v2 = new Vector3f(x + 1, y, z + 1).multLocal(f);
				Vector3f v3 = new Vector3f(x + 1, y, z).multLocal(f);

				Vector3f n = Vector3f.UNIT_Y;

				Vector4f t0 = new Vector4f(x, z, 0.0f, 1.0f).divideLocal(width / diviation).multLocal(f);
				Vector4f t1 = new Vector4f(x, z + 1, 0.0f, 1.0f).divideLocal(height / diviation).multLocal(f);
				Vector4f t2 = new Vector4f(x + 1, z + 1, 0.0f, 1.0f).divideLocal((width + height) / 2.0f / diviation).multLocal(f);
				Vector4f t3 = new Vector4f(x + 1, z, 0.0f, 1.0f).divideLocal(width / diviation).multLocal(f);

				ColorRGBA c = ColorRGBA.LIGHT_GRAY;

				vs.getData().addAll(v0, v1, v2, v2, v3, v0);
				ns.getData().addAll(n, n, n, n, n, n);
				ts.getData().addAll(t0, t1, t2, t2, t3, t0);
				cs.getData().addAll(c, c, c, c, c, c);
			}
		}
		vao.getBufferGroups().add(group);
		vao.compile();
		System.out.println("Vertex Creation: " + (System.currentTimeMillis() - s));
		compiled = true;
	}

	@Override
	public void render() {
		if (vao == null) {
			compile();
		}

		performUpdates();

		performSpectrumDisplacement();
		performNormalsFoldingCalculation();

		renderSurface();
	}

	/**
	 * Performs an omega and spectrum texture update if some properties have
	 * changed.
	 */
	public void performUpdates() {
		if (!hasChanged) {
			return;
		}
		long s = System.currentTimeMillis();
		omega = omega();
		spectrum = spectrum();
		butterfly = butterfly();
		System.out.println("Texture Creation: " + (System.currentTimeMillis() - s));

		heightfieldX = new ImageTexture2D(ImageData.Format.RG16F, dimension, dimension);
		heightfieldY = new ImageTexture2D(ImageData.Format.RG16F, dimension, dimension);
		heightfieldZ = new ImageTexture2D(ImageData.Format.RG16F, dimension, dimension);
		nfMap = new ImageTexture2D(ImageData.Format.RGBA16F, dimension, dimension);
		result = new ImageTexture2D(ImageData.Format.RGBA16F, dimension, dimension);

		heightfieldX.setMagFilter(Texture.MagFilter.NEAREST);
		heightfieldY.setMagFilter(Texture.MagFilter.NEAREST);
		heightfieldZ.setMagFilter(Texture.MagFilter.NEAREST);
		nfMap.setMagFilter(Texture.MagFilter.BILINEAR);
		result.setMagFilter(Texture.MagFilter.NEAREST);

		heightfieldX.setMinFilter(Texture.MinFilter.NEAREST);
		heightfieldY.setMinFilter(Texture.MinFilter.NEAREST);
		heightfieldZ.setMinFilter(Texture.MinFilter.NEAREST);
		nfMap.setMinFilter(Texture.MinFilter.NEAREST);
		result.setMinFilter(Texture.MinFilter.NEAREST);

		nfMap.setWrapMode(Texture.WrapAxis.S, Texture.WrapMode.REPEAT);
		nfMap.setWrapMode(Texture.WrapAxis.T, Texture.WrapMode.REPEAT);
		result.setWrapMode(Texture.WrapAxis.S, Texture.WrapMode.REPEAT);
		result.setWrapMode(Texture.WrapAxis.T, Texture.WrapMode.REPEAT);

		heightfieldX.setDepthTexture(false);
		heightfieldY.setDepthTexture(false);
		heightfieldZ.setDepthTexture(false);
		nfMap.setDepthTexture(false);
		result.setDepthTexture(false);

		for (int i = 0; i < samplerTextures.length; i++) {
			samplerTextures[i] = new Texture2D(new ImageData(ImageData.Format.RG16F, dimension, dimension, BufferUtilities.createByteBuffer(dimension * dimension * 2 * 4)));
			samplerTextures[i].setMinFilter(Texture.MinFilter.NEAREST);
			samplerTextures[i].setMagFilter(Texture.MagFilter.NEAREST);
			samplerTextures[i].setDepthTexture(false);
		}

		samplers = new OffscreenSampler[2];
		samplers[0] = new OffscreenSampler(samplerTextures[0], samplerTextures[2], samplerTextures[4], result);
		samplers[1] = new OffscreenSampler(samplerTextures[1], samplerTextures[3], samplerTextures[5], result);

		hasChanged = false;
	}

	/**
	 * Performs the phillips spectrum displacement.
	 */
	private void performSpectrumDisplacement() {
		//Do Spectrum Displacement
		spectrumShader.getParameters().clear();

		for (ComputeShader shader : spectrumShader.getShadersOfType(ComputeShader.class)) {
			shader.setWorkGroupsX(dimension / 8);
			shader.setWorkGroupsY(dimension / 8);
		}

		//Uniform params
		spectrumShader.getParameters().add(new Image2DParameter("m_HeightFieldX", heightfieldX));
		spectrumShader.getParameters().add(new Image2DParameter("m_HeightFieldY", heightfieldY));
		spectrumShader.getParameters().add(new Image2DParameter("m_HeightFieldZ", heightfieldZ));

		spectrumShader.getParameters().add(new Sampler2DParameter("m_SpectrumTexture", spectrum));
		spectrumShader.getParameters().add(new Sampler2DParameter("m_OmegaTexture", omega));

		spectrumShader.getParameters().add(new FloatParameter("m_Time", EngineTimer.getTickTime() / EngineTimer.SECOND_TO_NANO / 3.0f));
		spectrumShader.getParameters().add(new FloatParameter("m_Amplitude", 0.25f));
		spectrumShader.getParameters().add(new FloatParameter("m_Dimension", (float) dimension));

		spectrumShader.use();
		spectrumShader.unuse();
	}

	private void performNormalsFoldingCalculation() {
		//Uniform parameters
		nfShader.getParameters().clear();

		for (ComputeShader shader : nfShader.getShadersOfType(ComputeShader.class)) {
			shader.setWorkGroupsX(dimension / 8);
			shader.setWorkGroupsY(dimension / 8);
		}

		nfShader.getParameters().add(new Image2DParameter("m_DisplacementMap", result));
		nfShader.getParameters().add(new Image2DParameter("m_NormalsFoldingMap", nfMap));

		nfShader.getParameters().add(new Vec2Parameter("m_ChoppyScale", choppyScale));

		nfShader.getParameters().add(new FloatParameter("m_DistanceBetweenVertex", 40.0f / (dimension * 2.0f)));

		nfShader.use();
		nfShader.unuse();
	}

	/**
	 * Renders the water surface.
	 */
	private void renderSurface() {
		//Uniform parameters
		surfaceShader.getParameters().clear();

//		surfaceShader.getParameters().add(new AttributeLocationParameter("i_Vertex", vao.getBufferGroups().get(0).getBuffers().get(0)));
//		surfaceShader.getParameters().add(new AttributeLocationParameter("i_Normal", vao.getBufferGroups().get(0).getBuffers().get(1)));
//		surfaceShader.getParameters().add(new AttributeLocationParameter("i_TexCoord", vao.getBufferGroups().get(0).getBuffers().get(2)));
//		surfaceShader.getParameters().add(new AttributeLocationParameter("i_Color", vao.getBufferGroups().get(0).getBuffers().get(3)));
		surfaceShader.getParameters().add(new Mat3Parameter("m_ModelNormalMatrix", Pipeline.getNormalMatrix()));
		surfaceShader.getParameters().add(new Mat4Parameter("m_ModelViewProjectionMatrix", Pipeline.getModelViewProjectionMatrix()));

		surfaceShader.getParameters().add(new Sampler2DParameter("m_FoamMap", foammap));
//		surfaceShader.getParameters().add(new SamplerCubeParameter("m_SkyBox", sky));
		surfaceShader.getParameters().add(new Sampler2DParameter("m_DisplacementMap", result));
		surfaceShader.getParameters().add(new Sampler2DParameter("m_NormalsFoldingMap", nfMap));

		surfaceShader.getParameters().add(new Vec3Parameter("m_CameraPosition", cameraPosition));
		surfaceShader.getParameters().add(new Vec3Parameter("m_LightPosition", new Vector3f(-400, 200, -400)));

		surfaceShader.getParameters().add(new ColorRGBAParameter("m_LightColor", ColorRGBA.WHITE));
		surfaceShader.getParameters().add(new ColorRGBAParameter("m_DiffuseColor", diffuseColor));
		surfaceShader.getParameters().add(new ColorRGBAParameter("m_LowWaterColor", deepWaterColor));
		surfaceShader.getParameters().add(new ColorRGBAParameter("m_HighWaterColor", waterColor));

		surfaceShader.getParameters().add(new Vec2Parameter("m_PerlinNoiseScale", Vector2f.UNIT_XY.divide(perlinNoiseScale)));
		surfaceShader.getParameters().add(new Vec2Parameter("m_ChoppyScale", choppyScale));

		surfaceShader.getParameters().add(new FloatParameter("m_Time", EngineTimer.getTickTime() / EngineTimer.SECOND_TO_NANO));
		surfaceShader.getParameters().add(new FloatParameter("m_LightShininess", specularIntensity));
		surfaceShader.getParameters().add(new FloatParameter("m_Reflection", reflection));
		surfaceShader.getParameters().add(new FloatParameter("m_Transparency", transparency));
		surfaceShader.getParameters().add(new FloatParameter("m_Foam", foam));
//		surfaceShader.getParameters().add(new FloatParameter("m_FoamHeightModification", foamHeightModification));
		surfaceShader.getParameters().add(new FloatParameter("m_PerlinNoiseHeight", perlinNoiseHeight));
		surfaceShader.getParameters().add(new FloatParameter("m_PerlinNoiseAnimationSpeed", perlinNoiseAnimationSpeed));

		surfaceShader.getParameters().add(new FloatParameter("m_LevelOfDetailMinDistance", levelOfDetail.getMinDetailLevel()));
		surfaceShader.getParameters().add(new FloatParameter("m_LevelOfDetailMaxDistance", levelOfDetail.getMaxDetailLevel()));
		surfaceShader.getParameters().add(new FloatParameter("m_LevelOfDetailChangeDistance", levelOfDetail.getFarthestChangeDistance()));

		//Start shading and rendering
		surfaceShader.use();
		vao.render();
		surfaceShader.unuse();
	}

	public void setSpecularIntensity(float specularIntensity) {
		this.specularIntensity = specularIntensity;
	}

	public float getSpecularIntensity() {
		return specularIntensity;
	}

	/**
	 * Sets the size/quality of the spectrum texture.
	 * <br>
	 * The higher this value is, the more realistic the water will look. The<br>
	 * default value is int(512).
	 * <br>
	 * The size of the texture must a power of 2! If the size increases,<br>
	 * the performance will drop. This is because the<br>
	 * <code>inverse fast fourier transformation</code> will take longer to
	 * complete on the GPU.
	 *
	 * @param size Spectrum quality.
	 */
	public void setSpectrumQuality(int size) {
		if (!FastMath.isPowerOf2(size)) {
			throw new IllegalArgumentException("The size must be power of 2 (64, 128, 256, 512, 1024, ...)!");
		}
		if (size < 32) {
			throw new IllegalArgumentException("The size should not be below 32!");
		}
		dimension = size;
		butterflies = (int) FastMath.log2(size);

		hasChanged = true;
	}

	/**
	 * Returns the spectrum quality.
	 *
	 * @return Spectrum quality.
	 */
	public int getSpectrumQuality() {
		return dimension;
	}

	/**
	 * Sets the perlin noise size scale value.
	 * <br/>
	 * The perlin noise size scale is used to scale the perlin noise height<br/>
	 * field up or down.
	 *
	 * @param perlinNoiseScale Perlin noise size scale.
	 */
	public void setPerlinNoiseScale(Vector2f perlinNoiseScale) {
		this.perlinNoiseScale = perlinNoiseScale;
	}

	/**
	 * Returns the perlin noise size scale value.
	 *
	 * @return Perlin noise size scale.
	 */
	public Vector2f getPerlinNoiseScale() {
		return perlinNoiseScale;
	}

	/**
	 * Sets the perlin noise height scale value.
	 * <br/>
	 * The perlin noise height scale is used for adding a perlin noise<br/>
	 * factor to the simulation which will decrease the looks of repetition!
	 *
	 * @param perlinNoiseHeight Perlin noise height scale value.
	 */
	public void setPerlinNoiseHeight(float perlinNoiseHeight) {
		this.perlinNoiseHeight = perlinNoiseHeight;
	}

	/**
	 * Returns the perlin noise height scale value.
	 *
	 * @return Perlin noise height scale value.
	 */
	public float getPerlinNoiseHeight() {
		return perlinNoiseHeight;
	}

	/**
	 * Sets the animation speed of the perlin noise height field.
	 *
	 * @param perlinNoiseAnimationSpeed Perlin noise animation speed.
	 */
	public void setPerlinNoiseAnimationSpeed(float perlinNoiseAnimationSpeed) {
		this.perlinNoiseAnimationSpeed = perlinNoiseAnimationSpeed;
	}

	/**
	 * Returns the perlin noise animation speed.
	 *
	 * @return Perlin noise animation speed.
	 */
	public float getPerlinNoiseAnimationSpeed() {
		return perlinNoiseAnimationSpeed;
	}

	/**
	 * Sets the camera position.
	 * <br>
	 * The camera position is used to calculate the light reflection on<br>
	 * the water surface.
	 *
	 * @param cameraPosition Camera position.
	 */
	public void setCameraPosition(Vector3f cameraPosition) {
		this.cameraPosition = cameraPosition;
	}

	/**
	 * Returns the camera position.
	 *
	 * @return Camera position.
	 */
	public Vector3f getCameraPosition() {
		return cameraPosition;
	}

	/**
	 * Sets the gravity. This will influence the wave size.
	 *
	 * @param gravity Gravity.
	 */
	public void setGravity(float gravity) {
		this.gravity = gravity;

		hasChanged = true;
	}

	/**
	 * Returns the gravity.
	 *
	 * @return Gravity.
	 */
	public float getGravity() {
		return gravity;
	}

	/**
	 * Sets the patch size. Default is float(512).
	 *
	 * @param patchSize Patch size.
	 */
	public void setPatchSize(float patchSize) {
		this.patchSize = patchSize;

		hasChanged = true;
	}

	/**
	 * Returns the patch size.
	 *
	 * @return Patch size.
	 */
	public float getPatchSize() {
		return patchSize;
	}

	/**
	 * Sets the wind speed.
	 * <br>
	 * This will influence the wave length and height.
	 *
	 * @param windSpeed Wind speed.
	 */
	public void setWindSpeed(float windSpeed) {
		this.windSpeed = windSpeed;

		hasChanged = true;
	}

	/**
	 * Returns the wind speed.
	 *
	 * @return Wind speed.
	 */
	public float getWindSpeed() {
		return windSpeed;
	}

	/**
	 * Sets the amplitude.
	 * <br>
	 * This will influence the wave height.
	 *
	 * @param amplitude Amplitude.
	 */
	public void setAmplitude(float amplitude) {
		this.amplitude = amplitude;

		hasChanged = true;
	}

	/**
	 * Returns the amplitude.
	 *
	 * @return Amplitude.
	 */
	public float getAmplitude() {
		return amplitude;
	}

	/**
	 * Sets how much the reflection texture should be used.
	 * <br>
	 * The default value is float(0.15). The value must be between float(0.0)
	 * and float(1.0).
	 *
	 * @param reflection Reflection factor.
	 */
	public void setReflection(float reflection) {
		if (reflection < 0.0 || reflection > 1.0) {
			throw new IllegalArgumentException("The reflection factor must be between 0.0 and 1.0!");
		}
		this.reflection = reflection;
	}

	/**
	 * Returns the reflection factor.
	 *
	 * @return Reflection factor.
	 */
	public float getReflection() {
		return reflection;
	}

	/**
	 * Sets how much the foam texture should be blended with the water color.
	 * <br>
	 * The default value is float(0.75). The value must be between float(0.0)
	 * and float(1.0).
	 *
	 * @param foam Foam factor.
	 */
	public void setFoam(float foam) {
		if (foam < 0.0 || foam > 1.0) {
			throw new IllegalArgumentException("The foam factor must be between 0.0 and 1.0!");
		}
		this.foam = foam;
	}

	/**
	 * Returns the foam factor.
	 *
	 * @return Foam factor.
	 */
	public float getFoam() {
		return foam;
	}

	/**
	 * Sets the foam height modification value.
	 * <br>
	 * The higher this value, the later the foam will be visible. If the<br>
	 * value is extrem high, only on top of waves, will the foam be visible.
	 * <br><br>
	 * If the value is low, the more visible the foam will be.<br>
	 * The default value is float(1.5).
	 *
	 * @param foamHeightModification Foam height modification.
	 */
	public void setFoamHeightModification(float foamHeightModification) {
		this.foamHeightModification = foamHeightModification;
	}

	/**
	 * Returns the foam height modification value.
	 *
	 * @return Foam height modification.
	 */
	public float getFoamHeightModification() {
		return foamHeightModification;
	}

	/**
	 * Sets the wind direction.
	 *
	 * @param windDirection Wind direction.
	 */
	public void setWindDirection(Vector2f windDirection) {
		this.windDirection = windDirection;

		hasChanged = true;
	}

	/**
	 * Returns the wind direction.
	 *
	 * @return Wind direction.
	 */
	public Vector2f getWindDirection() {
		return windDirection;
	}

	/**
	 * Sets the choppy scale factors.
	 * <br>
	 * The lower these values, the less choppyness will occur on the waves.<br>
	 * The default value is Vector2f(1.0, 1.0).
	 *
	 * @param choppyScale Choppy scale.
	 */
	public void setChoppyScale(Vector2f choppyScale) {
		this.choppyScale = choppyScale;
	}

	/**
	 * Returns the choppy scale factors.
	 *
	 * @return Choppy scale.
	 */
	public Vector2f getChoppyScale() {
		return choppyScale;
	}

	/**
	 * Sets the diffuse color.
	 * <br>
	 * The water color will be multiplied with the diffuse color. This can<br>
	 * generate very dark water surfaces or really bright surfaces.
	 * <br>
	 * Default color is ColorRGBA(1, 1, 1).
	 *
	 * @param diffuseColor Diffuse color.
	 */
	public void setDiffuseColor(ColorRGBA diffuseColor) {
		this.diffuseColor = diffuseColor;
	}

	/**
	 * Returns the diffuse color.
	 *
	 * @return Diffuse color.
	 */
	public ColorRGBA getDiffuseColor() {
		return diffuseColor;
	}

	/**
	 * Sets the water color.
	 * <br>
	 * This color will be used on top of waves. That means, the water color<br>
	 * should always be lighter than the deep water color.
	 * <br>
	 * The brighter this color, the more light passes through the waves.
	 *
	 * @param waterColor Water color.
	 */
	public void setWaterColor(ColorRGBA waterColor) {
		this.waterColor = waterColor;
	}

	/**
	 * Returns the water color.
	 *
	 * @return Water color.
	 */
	public ColorRGBA getWaterColor() {
		return waterColor;
	}

	/**
	 * Sets the deep water color.
	 * <br>
	 * This color will be used on the locations where no waves are. The<br>
	 * deeper the position, the more this color will occur. This means,<br>
	 * the deep water color should always be darker than the water color.
	 * <br>
	 * The darker this color, the less light passes through and the<br>
	 * deeper the water will look like.
	 *
	 * @param deepWaterColor Deep water color.
	 */
	public void setDeepWaterColor(ColorRGBA deepWaterColor) {
		this.deepWaterColor = deepWaterColor;
	}

	/**
	 * Returns the deep water color.
	 *
	 * @return Deep water color.
	 */
	public ColorRGBA getDeepWaterColor() {
		return deepWaterColor;
	}

	/**
	 * Sets the foam map. The foam map will be blended with the water color<br>
	 * using the given foam factor.
	 *
	 * @param foammap Foam map.
	 */
	public void setFoamMap(Texture2D foammap) {
		if (foammap == null) {
			throw new NullPointerException("Foam map can not be null!");
		}
		this.foammap = foammap;
	}

	/**
	 * Returns the foam map.
	 *
	 * @return Foam map.
	 */
	public Texture2D getFoamMap() {
		return foammap;
	}

	/**
	 * Sets the sky texture.
	 * <br>
	 * This given texture will be converted into a cube map texture which<br>
	 * has on all six sides the same texture. The sky box will be used for<br>
	 * reflection.
	 *
	 * @param sky Sky texture.
	 */
	public void setSkyMap(Texture2D sky) {
		this.sky = new TextureCubeMap(sky, sky, sky, sky, sky, sky);
	}

	/**
	 * Sets the sky texture.
	 * <br>
	 * The given sky texture will be used for reflection. The cube map<br>
	 * texture can be queried by <code>Box#getTextureAsCubeMap()</code>.
	 *
	 * @param sky Sky texture.
	 */
	public void setSkyMap(TextureCubeMap sky) {
		this.sky = sky;
	}

	/**
	 * Returns the sky texture.
	 *
	 * @return Sky texture.
	 */
	public TextureCubeMap getSkyMap() {
		return sky;
	}

	/**
	 * Returns the number of butterflies.
	 *
	 * @return Butterflies.
	 */
	public int getButterflies() {
		return butterflies;
	}

	/**
	 * Sets the spectrum algorithm.
	 * <br>
	 * The spectrum algorithm is used to create the spectrum texture for<br>
	 * the water heightfield generation.
	 *
	 * @param spectrumAlgorithm Spectrum algorithm.
	 */
	public void setSpectrumAlgorithm(SpectrumAlgorithm spectrumAlgorithm) {
		this.spectrumAlgorithm = spectrumAlgorithm;
	}

	/**
	 * Returns the spectrum algorithm.
	 *
	 * @return Spectrum algorithm.
	 */
	public SpectrumAlgorithm getSpectrumAlgorithm() {
		return spectrumAlgorithm;
	}

	/**
	 * Sets the grid diviation.
	 *
	 * @param diviation Grid diviation.
	 */
	public void setGridDiviation(float diviation) {
		this.diviation = diviation;
	}

	/**
	 * Returns the grid diviation.
	 *
	 * @return Grid diviation.
	 */
	public float getGridDiviation() {
		return diviation;
	}

	/**
	 * Sets the tessellation level of detail properties.
	 *
	 * @param levelOfDetail Level of detail.
	 */
	public void setLevelOfDetail(TessellationLevelOfDetail levelOfDetail) {
		this.levelOfDetail = levelOfDetail;
	}

	/**
	 * Returns the used tessellation level of detail.
	 *
	 * @return Level of detail.
	 */
	public TessellationLevelOfDetail getLevelOfDetail() {
		return levelOfDetail;
	}

	/**
	 * Sets the surface transparency.
	 *
	 * @param transparency Surface transparency.
	 */
	public void setTransparency(float transparency) {
		this.transparency = transparency;
	}

	/**
	 * Returns the surface transparency.
	 *
	 * @return Surface transparency.
	 */
	public float getTransparency() {
		return transparency;
	}

	@Override
	public boolean hasTransparency() {
		return deepWaterColor.hasTransparency() || waterColor.hasTransparency() || transparency < 1.0f;
	}

	/**
	 * Creates and returns the phillips spectrum texture.
	 *
	 * @return Phillips spectrum texture.
	 */
	private Texture2D spectrum() {
		FloatBuffer buffer = BufferUtilities.createFloatBuffer(dimension * dimension * 2);
		FastRandom rnd = new FastRandom(4893555064671L);
		//4893555064671L
		System.out.println(rnd.getSeed());

		float x, y;
		float nd = -dimension / 2.0f;
		float fa = 2.0f * FastMath.PI_FLOAT / patchSize;

		for (int i = 0; i < dimension; i++) {
			x = (nd + i) * fa;
			for (int j = 0; j < dimension; j++) {
				y = (nd + j) * fa;

				float phillips = (x == 0 && y == 0) ? 0.0f : FastMath.sqrt(spectrumAlgorithm.spectrum(this, new Vector2f(x, y)));

				buffer.put(phillips * rnd.nextGaussian() * FastMath.INV_SQRT_2_FLOAT);
				buffer.put(phillips * rnd.nextGaussian() * FastMath.INV_SQRT_2_FLOAT);
			}
		}

		Texture2D t = new Texture2D(new ImageData(ImageData.Format.RG16F, dimension, dimension, BufferUtilities.asByteBuffer(buffer)));
		t.setMagFilter(Texture.MagFilter.NEAREST);
		t.setMinFilter(Texture.MinFilter.NEAREST);
		return t;
	}

	/**
	 * Creates the omega texture.
	 *
	 * @return Omega texture.
	 */
	private Texture2D omega() {
		FloatBuffer buffer = BufferUtilities.createFloatBuffer(dimension * dimension);

		float x, y;
		float nd = -dimension / 2.0f;
		float fa = 2.0f * FastMath.PI_FLOAT / patchSize;
		for (int i = 0; i < dimension; i++) {
			x = (nd + i) * fa;
			for (int j = 0; j < dimension; j++) {
				y = (nd + j) * fa;

				buffer.put(FastMath.sqrt((gravity * 100.0f) * FastMath.sqrt(x * x + y * y)));
			}
		}

		Texture2D t = new Texture2D(new ImageData(ImageData.Format.R32F, dimension, dimension, BufferUtilities.asByteBuffer(buffer)));
		t.setMagFilter(Texture.MagFilter.NEAREST);
		t.setMinFilter(Texture.MinFilter.NEAREST);
		return t;
	}

	/**
	 * Creates and returns the butterfly texture.
	 *
	 * @return Butterfly texture.
	 */
	private Texture2D butterfly() {
		float[][] indices = indices(butterflies, 2 * dimension);
		float[][] weights = weights(butterflies, 2 * dimension);

		float[] butterflyArray = new float[dimension * butterflies * 4];
		for (int y = 0; y < butterflies; y++) {
			int rowAdd = 4 * y * dimension;
			for (int x = 0; x < dimension; x++) {
				int colAdd = 4 * x;
				butterflyArray[rowAdd + colAdd + 0] = indices[y][2 * x] / dimension;
				butterflyArray[rowAdd + colAdd + 1] = indices[y][2 * x + 1] / dimension;
				butterflyArray[rowAdd + colAdd + 2] = weights[y][2 * x];
				butterflyArray[rowAdd + colAdd + 3] = weights[y][2 * x + 1];
			}
		}

		Texture2D t = new Texture2D(new ImageData(ImageData.Format.RGBA32F, dimension, butterflies, BufferUtilities.asByteBuffer(butterflyArray)));
		t.setMagFilter(Texture.MagFilter.NEAREST);
		t.setMinFilter(Texture.MinFilter.NEAREST);
		return t;
	}

	/**
	 * Creates all indices for the butterfly transformation in the FFT.
	 *
	 * @param numButterflies Number of butterflies.
	 * @param indices Number of indices.
	 * @return Indices.
	 */
	private float[][] indices(int numButterflies, int indices) {
		float[][] res = new float[numButterflies][indices];

		int iterations = indices / 2;
		int offset = 1;
		int start, end;
		int step, ip;

		for (int i = 0; i < numButterflies; i++) {
			iterations >>= 1;
			step = 2 * offset;
			end = step;
			start = 0;
			ip = 0;
			for (int j = 0; j < iterations; j++) {
				for (int k = start, l = 0, v = ip; k < end; k += 2, l += 2, v++) {
					res[i][k] = v;
					res[i][k + 1] = v + offset;
					res[i][l + end] = v;
					res[i][l + end + 1] = v + offset;
				}
				start += 2 * step;
				end += 2 * step;
				ip += step;
			}
			offset <<= 1;
		}

		reverse(res[0], numButterflies);

		return res;
	}

	/**
	 * Creates all weights for the butterfly transformation in the FFT.
	 *
	 * @param numButterflies Number of butterflies.
	 * @param weights Number of weights.
	 * @return Weights.
	 */
	private float[][] weights(int numButterflies, int weights) {
		float[][] res = new float[numButterflies][weights];

		int iterations = weights / 4;
		int numk = 1;
		int start, end;

		for (int i = 0; i < numButterflies; i++) {
			start = 0;
			end = 2 * numk;
			for (int j = 0; j < iterations; j++) {
				int kk = 0;
				for (int k = start; k < end; k += 2) {
					double v = 2.0 * FastMath.PI * kk * iterations / dimension;

					res[i][k] = (float) FastMath.cos(v);
					res[i][k + 1] = (float) -FastMath.sin(v);
					res[i][k + 2 * numk] = (float) -FastMath.cos(v);
					res[i][k + 2 * numk + 1] = (float) FastMath.sin(v);

					kk++;
				}
				start += 4 * numk;
				end = start + 2 * numk;
			}
			iterations >>= 1;
			numk <<= 1;
		}

		return res;
	}

	/**
	 * Reverses the bit order.
	 *
	 * @param indices Indices.
	 * @param numButterflies Number of butterflies.
	 */
	private void reverse(float[] indices, int numButterflies) {
		int mask = 0x1;
		for (int j = 0; j < indices.length; j++) {
			int val = 0x0;
			int temp = (int) indices[j];
			for (int i = 0; i < numButterflies; i++) {
				int t = (mask & temp);
				val = (val << 1) | t;
				temp >>= 1;
			}
			indices[j] = val;
		}
	}
}
