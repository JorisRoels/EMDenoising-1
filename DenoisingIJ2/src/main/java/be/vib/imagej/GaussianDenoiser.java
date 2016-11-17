package be.vib.imagej;

import be.vib.bits.QFunction;
import be.vib.bits.QHost;
import be.vib.bits.QUtils;
import be.vib.bits.QValue;

class GaussianDenoiser extends Denoiser
{
	final GaussianParams params;
	
	public GaussianDenoiser(LinearImage image, GaussianParams params)
	{
		super(image);
		this.params = params;
	}
	
	@Override
	public byte[] call()
	{
		if (!QHost.functionExists("gaussian_filter"))
		{
			// Lazy loading of the source module for this denoising function.
			// Once it is loaded it will persist in the Quasar host
			// even beyond the lifetime of this GaussianDenoiser object.
			QHost.loadSourceModule("E:\\git\\DenoisingIJ2Repository\\DenoisingIJ2\\src\\main\\resources\\quasar\\gaussian_filter.q");
		}
		
		assert(QHost.functionExists("gaussian_filter"));
		
		QFunction gaussian = new QFunction("gaussian_filter(mat,scalar,int,string)");

		QValue imageCube = QUtils.newCubeFromGrayscaleArray(image.width, image.height, image.pixels);
		
		QValue result = gaussian.apply(imageCube,
				                       new QValue(params.sigma),
				                       new QValue(0),
				                       new QValue("mirror"));
		
		byte[] outputPixels = QUtils.newGrayscaleArrayFromCube(image.width, image.height, result);
		
		result.delete();
		imageCube.delete();		
		
		return outputPixels;
	}
}