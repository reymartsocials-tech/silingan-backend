package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ria.olita.tech.silingan.config.OnboardingProperties;

import java.util.Map;

/**
 * Proves the branded "Dark Background" invitation card is composed as approved and, more
 * importantly, that the complete finished image still decodes to the exact join target. The centre
 * brand mark covers part of the matrix, so decoding the finished PNG - never an intermediate
 * unbranded QR - is the test that matters.
 */
class BrandedQrRendererTest {

	private static final String JOIN_URL = "https://app.silingan.ph/join?token=" + JoinQrTestFixtures.SAMPLE_TOKEN;

	/** Longest target the configuration can produce: web join URL plus a maximum-length token. */
	private static final String LONGEST_JOIN_URL =
		"https://app.silingan.ph/join?token=" + "A".repeat(128);

	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

	private final OnboardingProperties properties = JoinQrTestFixtures.properties();
	private final BrandedQrRenderer renderer = JoinQrTestFixtures.renderer(properties);
	private final QrCodeGenerator generator = new QrCodeGeneratorImpl(properties, renderer);

	private OnboardingProperties.Qr qr() {
		return properties.getQr();
	}

	private BufferedImage card() throws Exception {
		return JoinQrTestFixtures.read(generator.generatePng(JOIN_URL));
	}

	private BrandedQrRenderer.Layout layout() throws Exception {
		BitMatrix matrix = new QRCodeWriter().encode(
			JOIN_URL, BarcodeFormat.QR_CODE, 1, 1,
			Map.of(
				EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
				EncodeHintType.MARGIN, qr().getMarginModules()
			)
		);
		return renderer.layout(matrix.getWidth());
	}

	private static BufferedImage scaled(BufferedImage source, double factor) {
		int width = (int) Math.round(source.getWidth() * factor);
		int height = (int) Math.round(source.getHeight() * factor);
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		Graphics2D graphics = target.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(source, 0, 0, width, height, null);
		graphics.dispose();

		return target;
	}

	/** Approximates what a messaging app does to a shared image. */
	private static BufferedImage jpegRoundTrip(BufferedImage source, float quality) throws Exception {
		ImageWriter writer = null;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
		writer = writers.next();

		ImageWriteParam params = writer.getDefaultWriteParam();
		params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		params.setCompressionQuality(quality);

		try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(source, null, null), params);
		} finally {
			writer.dispose();
		}

		return ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
	}

	/** Approximates a screenshot: the card pasted into a larger phone-sized frame. */
	private static BufferedImage insideScreenshot(BufferedImage card, Color frame) {
		BufferedImage screenshot = new BufferedImage(
			card.getWidth() + 160, card.getHeight() + 320, BufferedImage.TYPE_INT_RGB
		);

		Graphics2D graphics = screenshot.createGraphics();
		graphics.setColor(frame);
		graphics.fillRect(0, 0, screenshot.getWidth(), screenshot.getHeight());
		graphics.drawImage(card, 80, 200, null);
		graphics.dispose();

		return screenshot;
	}

	@Test
	void producesAValidNonEmptyPngOnTheApprovedCanvas() throws Exception {
		byte[] png = generator.generatePng(JOIN_URL);

		assertThat(png).isNotEmpty();
		assertThat(java.util.Arrays.copyOf(png, PNG_MAGIC.length)).isEqualTo(PNG_MAGIC);

		BufferedImage card = JoinQrTestFixtures.read(png);
		assertThat(card.getWidth()).isEqualTo(qr().getCanvasWidth());
		assertThat(card.getHeight()).isEqualTo(qr().getCanvasHeight());
	}

	@Test
	void staysSmallEnoughToShareOverMessagingAndEmail() {
		assertThat(generator.generatePng(JOIN_URL).length).isLessThan(500 * 1024);
	}

	@Test
	void paintsTheDarkSilinganBackgroundAcrossTheCard() throws Exception {
		BufferedImage card = card();
		Color background = qr().backgroundColorValue();

		assertThat(new Color(card.getRGB(0, 0))).isEqualTo(background);
		assertThat(new Color(card.getRGB(card.getWidth() - 1, 0))).isEqualTo(background);
		assertThat(new Color(card.getRGB(0, card.getHeight() - 1))).isEqualTo(background);
		assertThat(new Color(card.getRGB(card.getWidth() - 1, card.getHeight() - 1))).isEqualTo(background);
	}

	@Test
	void centresTheQrPanelHorizontallyInsideTheSidePadding() throws Exception {
		BrandedQrRenderer.Layout layout = layout();

		int leftGap = layout.qrX();
		int rightGap = layout.canvasWidth() - (layout.qrX() + layout.qrPx());

		// Odd leftover pixels can differ by one; the panel is still visually centred.
		assertThat(leftGap).isCloseTo(rightGap, org.assertj.core.data.Offset.offset(1));
		assertThat(leftGap).isGreaterThanOrEqualTo(qr().getSidePadding());
		assertThat(layout.qrPx()).isLessThanOrEqualTo(qr().getQrSize());
	}

	@Test
	void scalesTheMatrixByWholePixelsPerModule() throws Exception {
		BrandedQrRenderer.Layout layout = layout();

		assertThat(layout.moduleScale()).isGreaterThanOrEqualTo(1);
		assertThat(layout.qrPx()).isEqualTo(layout.moduleScale() * layout.matrixModules());
	}

	@Test
	void keepsTheQuietZoneClearOnEverySideOfTheMatrix() throws Exception {
		BufferedImage card = card();
		BrandedQrRenderer.Layout layout = layout();
		Color panel = qr().qrBackgroundColorValue();

		int quietZone = qr().getMarginModules() * layout.moduleScale();
		int centre = layout.qrX() + layout.qrPx() / 2;

		// Sampled one module inside the quiet zone to stay clear of the rounded panel corners.
		for (int offset = layout.moduleScale(); offset < quietZone; offset++) {
			assertThat(new Color(card.getRGB(centre, layout.qrY() + offset))).isEqualTo(panel);
			assertThat(new Color(card.getRGB(centre, layout.qrY() + layout.qrPx() - 1 - offset))).isEqualTo(panel);
			assertThat(new Color(card.getRGB(layout.qrX() + offset, centre))).isEqualTo(panel);
			assertThat(new Color(card.getRGB(layout.qrX() + layout.qrPx() - 1 - offset, centre))).isEqualTo(panel);
		}
	}

	@Test
	void keepsTheCentreBrandPlateWithinTheApprovedRatioAndCentred() throws Exception {
		BrandedQrRenderer.Layout layout = layout();

		double ratio = layout.logoPlateSize() / (double) layout.qrPx();
		assertThat(ratio).isLessThanOrEqualTo(OnboardingProperties.Qr.CENTER_LOGO_MAX_RATIO_LIMIT);
		assertThat(ratio).isGreaterThan(0.10);

		int plateCentreX = layout.logoPlateX() + layout.logoPlateSize() / 2;
		int plateCentreY = layout.logoPlateY() + layout.logoPlateSize() / 2;
		assertThat(plateCentreX).isCloseTo(layout.qrX() + layout.qrPx() / 2, org.assertj.core.data.Offset.offset(2));
		assertThat(plateCentreY).isCloseTo(layout.qrY() + layout.qrPx() / 2, org.assertj.core.data.Offset.offset(2));
	}

	@Test
	void keepsTheCentreBrandPlateAwayFromEveryFinderPattern() throws Exception {
		BrandedQrRenderer.Layout layout = layout();

		int scale = layout.moduleScale();
		int margin = qr().getMarginModules();
		int finder = 7;
		int lastFinderStart = layout.matrixModules() - margin - finder;

		int[][] finderOrigins = {
			{margin, margin},
			{lastFinderStart, margin},
			{margin, lastFinderStart}
		};

		for (int[] origin : finderOrigins) {
			int x = layout.qrX() + origin[0] * scale;
			int y = layout.qrY() + origin[1] * scale;
			int size = finder * scale;

			boolean overlaps = x < layout.logoPlateX() + layout.logoPlateSize()
				&& layout.logoPlateX() < x + size
				&& y < layout.logoPlateY() + layout.logoPlateSize()
				&& layout.logoPlateY() < y + size;

			assertThat(overlaps).isFalse();
		}
	}

	@Test
	void drawsTheBrandMarkInsideTheWhitePlate() throws Exception {
		BufferedImage card = card();
		BrandedQrRenderer.Layout layout = layout();

		int centreX = layout.logoPlateX() + layout.logoPlateSize() / 2;
		int centreY = layout.logoPlateY() + layout.logoPlateSize() / 2;

		boolean brandMarkPixelFound = false;
		for (int y = layout.logoPlateY(); y < layout.logoPlateY() + layout.logoPlateSize(); y++) {
			for (int x = layout.logoPlateX(); x < layout.logoPlateX() + layout.logoPlateSize(); x++) {
				Color pixel = new Color(card.getRGB(x, y));
				if (pixel.getGreen() > pixel.getRed() && pixel.getGreen() > 40 && pixel.getGreen() < 200) {
					brandMarkPixelFound = true;
				}
			}
		}

		assertThat(brandMarkPixelFound).as("logo pixels inside the plate").isTrue();
		// Plate corners stay white, so the mark never bleeds past its backing.
		assertThat(new Color(card.getRGB(centreX, layout.logoPlateY() + 1)))
			.isEqualTo(qr().qrBackgroundColorValue());
		assertThat(new Color(card.getRGB(layout.logoPlateX() + 1, centreY)))
			.isEqualTo(qr().qrBackgroundColorValue());
	}

	@Test
	void rendersTheWordmarkAndTaglineBelowTheQrAndInsideTheCanvas() throws Exception {
		BufferedImage card = card();
		BrandedQrRenderer.Layout layout = layout();

		assertThat(layout.wordmarkBaselineY()).isGreaterThan(layout.qrY() + layout.qrPx());
		assertThat(layout.taglineBaselineY()).isGreaterThan(layout.wordmarkBaselineY());
		assertThat(layout.taglineBaselineY()).isLessThan(layout.canvasHeight() - 1);

		assertThat(lightPixelsInBand(card, layout.wordmarkBaselineY() - qr().getWordmarkFontSize(),
			layout.wordmarkBaselineY())).isGreaterThan(0);
		assertThat(lightPixelsInBand(card, layout.taglineBaselineY() - qr().getTaglineFontSize(),
			layout.taglineBaselineY())).isGreaterThan(0);

		// Nothing is drawn into the side padding, so no glyph is clipped by the canvas edge.
		for (int y = layout.qrY() + layout.qrPx(); y < layout.canvasHeight(); y++) {
			assertThat(new Color(card.getRGB(2, y))).isEqualTo(qr().backgroundColorValue());
			assertThat(new Color(card.getRGB(card.getWidth() - 3, y))).isEqualTo(qr().backgroundColorValue());
		}
	}

	private static int lightPixelsInBand(BufferedImage card, int fromY, int toY) {
		int count = 0;
		for (int y = Math.max(0, fromY); y < Math.min(card.getHeight(), toY); y++) {
			for (int x = 0; x < card.getWidth(); x++) {
				Color pixel = new Color(card.getRGB(x, y));
				if (pixel.getRed() > 200 && pixel.getGreen() > 200 && pixel.getBlue() > 200) {
					count++;
				}
			}
		}
		return count;
	}

	@Test
	void theCompleteBrandedImageDecodesToTheExactJoinUrl() throws Exception {
		assertThat(JoinQrTestFixtures.decode(generator.generatePng(JOIN_URL))).isEqualTo(JOIN_URL);
	}

	@Test
	void decodesTheLongestExpectedJoinUrl() throws Exception {
		assertThat(JoinQrTestFixtures.decode(generator.generatePng(LONGEST_JOIN_URL))).isEqualTo(LONGEST_JOIN_URL);
	}

	@Test
	void keepsSeparateInvitationsDecodingToTheirOwnTarget() throws Exception {
		String first = "silingan://join?token=" + "a".repeat(43);
		String second = "silingan://join?token=" + "b".repeat(43);

		byte[] firstPng = generator.generatePng(first);
		byte[] secondPng = generator.generatePng(second);

		assertThat(JoinQrTestFixtures.decode(firstPng)).isEqualTo(first);
		assertThat(JoinQrTestFixtures.decode(secondPng)).isEqualTo(second);
		assertThat(firstPng).isNotEqualTo(secondPng);
	}

	@ParameterizedTest
	@ValueSource(doubles = {0.75, 0.5})
	void decodesAfterBeingDownscaled(double factor) throws Exception {
		BufferedImage downscaled = scaled(card(), factor);

		assertThat(JoinQrTestFixtures.decode(downscaled)).isEqualTo(JOIN_URL);
	}

	@Test
	void decodesAfterMessagingStyleJpegCompression() throws Exception {
		BufferedImage compressed = jpegRoundTrip(card(), 0.82f);

		assertThat(JoinQrTestFixtures.decode(compressed)).isEqualTo(JOIN_URL);
	}

	@Test
	void decodesWhenCapturedInsideALargerScreenshot() throws Exception {
		assertThat(JoinQrTestFixtures.decode(insideScreenshot(card(), Color.WHITE))).isEqualTo(JOIN_URL);
		assertThat(JoinQrTestFixtures.decode(insideScreenshot(card(), new Color(0x121212)))).isEqualTo(JOIN_URL);
	}

	@Test
	void brandingNeverLeaksIntoTheEncodedPayload() throws Exception {
		String decoded = JoinQrTestFixtures.decode(generator.generatePng(JOIN_URL));

		assertThat(decoded)
			.isEqualTo(JOIN_URL)
			.doesNotContain(BrandedQrRenderer.WORDMARK)
			.doesNotContain(qr().getTagline())
			.doesNotContain("communityCode");
	}
}
