package org.janelia.render.client.emshading;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.janelia.alignment.filter.emshading.FourthOrderShading;
import org.janelia.alignment.filter.emshading.QuadraticShading;
import org.janelia.alignment.filter.emshading.ShadingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class ShadingModelProvider implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(ShadingModelProvider.class);

	private final List<ModelSpec> sortedModelSpecs;

	private ShadingModelProvider(final List<ModelSpec> modelSpecs) {
		this.sortedModelSpecs = modelSpecs;
		this.sortedModelSpecs.sort(Collections.reverseOrder(Comparator.comparingInt(ModelSpec::getZ)));
	}

	public ShadingModel getModel(final int z) {
		for (final ModelSpec modelSpec : sortedModelSpecs) {
			if (z >= modelSpec.getZ()) {
				return modelSpec.getModel();
			}
		}
		throw new IllegalArgumentException("No model found for z=" + z);
	}

	public static ShadingModelProvider fromJsonFile(final String fileName) throws IOException {
		LOG.info("Reading model specs from file: {}", fileName);
		final ObjectMapper mapper = new ObjectMapper();
		try (final FileReader reader = new FileReader(fileName)) {
			return fromJson(mapper.readTree(reader));
		}
	}

	/**
	 * Provide a {@link ShadingModel} for a given z value. If no model is found for the given z value, the provider
	 * returns null.
	 */
	public static ShadingModelProvider fromJson(final JsonNode jsonData) throws JsonProcessingException {

		final ObjectMapper mapper = new ObjectMapper();
		final List<ModelSpec> modelSpecs = new ArrayList<>(Arrays.asList(mapper.treeToValue(jsonData, ModelSpec[].class)));

		// validation of json data
		for (final ModelSpec modelSpec : modelSpecs) {
			LOG.debug("Found model spec: {}", modelSpec);
			final ShadingModel ignored = modelSpec.getModel();
		}

		if (modelSpecs.isEmpty()) {
			throw new IllegalArgumentException("No model specs found in json data");
		}

		return new ShadingModelProvider(modelSpecs);
	}


	@SuppressWarnings("unused")
	private static class ModelSpec implements Serializable {
		@JsonProperty("z")
		private int z;
		@JsonProperty("modelType")
		private String modelType;
		@JsonProperty("coefficients")
		private double[] coefficients;

		// no explicit constructor; meant to be deserialized from json

		public int getZ() {
			return z;
		}

		public void setCoefficients(final double[] coefficients) {
			this.coefficients = coefficients;
		}

		public ShadingModel getModel() {
			switch (modelType) {
				case "quadratic":
					return new QuadraticShading(coefficients);
				case "fourthOrder":
					return new FourthOrderShading(coefficients);
				case "none":
					return null;
				default:
					throw new IllegalArgumentException("Unknown model type: " + modelType);
			}
		}

		public String toString() {
			return "ModelSpec{z=" + z + ", modelType=" + modelType + ", coefficients=" + Arrays.toString(coefficients) + "}";
		}
	}
}
