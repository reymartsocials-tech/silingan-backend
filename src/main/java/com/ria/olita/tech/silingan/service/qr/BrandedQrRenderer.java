package com.ria.olita.tech.silingan.service.qr;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.common.BitMatrix;
import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.exception.QrCodeGenerationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composes an encoded join QR matrix into the approved SILINGAN "Dark Background" invitation card:
 * a dark branded canvas, a high-contrast QR panel carrying the official mark in its centre, and the
 * SILINGAN wordmark and tagline underneath.
 *
 * <p>Branding is purely visual. The renderer receives an already encoded {@link BitMatrix} and never
 * sees, alters or logs the join target, so the finished image always decodes to exactly the payload
 * the invitation service produced.
 *
 * <p>Scanning reliability drives the composition: modules are drawn as whole pixels at an integer
 * module scale with antialiasing off, the four-module quiet zone of the matrix is preserved inside
 * the white panel, and the centre mark stays within the configured ratio so it cannot reach the
 * finder, timing or alignment patterns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BrandedQrRenderer {

	/** Wordmark rendered by the renderer; the packaged asset is the icon only. */
	public static final String WORDMARK = "SILINGAN";

	private static final String PNG_FORMAT = "PNG";
	private static final int PANEL_CORNER_RADIUS = 28;
	private static final int PLATE_CORNER_RADIUS = 10;
	private static final float WORDMARK_TRACKING = 0.14f;
	private static final double TEXT_MAX_WIDTH_RATIO = 0.6875;

	private final OnboardingProperties onboardingProperties;
	private final BrandLogoProvider brandLogoProvider;

	/**
	 * Renders the branded invitation card for an encoded QR matrix.
	 *
	 * @param matrix matrix including its quiet zone, at one pixel per module.
	 * @return PNG bytes of the complete branded card.
	 */
	public byte[] renderPng(BitMatrix matrix) {
		OnboardingProperties.Qr qr = onboardingProperties.getQr();
		BufferedImage canvas = new BufferedImage(qr.getCanvasWidth(), qr.getCanvasHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = canvas.createGraphics();

		try {
			Layout layout = layout(matrix.getWidth(), graphics);

			graphics.setColor(qr.backgroundColorValue());
			graphics.fillRect(0, 0, qr.getCanvasWidth(), qr.getCanvasHeight());

			drawQrPanel(graphics, matrix, layout, qr);
			drawCenterLogo(graphics, layout, qr);
			drawBranding(graphics, layout, qr);
		} finally {
			graphics.dispose();
		}

		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(canvas, PNG_FORMAT, output);
			return output.toByteArray();
		} catch (IOException ex) {
			log.error("Branded QR composition failed: {}", ex.getMessage());
			throw new QrCodeGenerationException("Unable to generate QR code");
		}
	}

	/**
	 * Resolves every position from the configured dimensions, so the composition has no scattered
	 * magic coordinates and stays correct when the canvas or QR size is tuned.
	 *
	 * @param matrixModules matrix width in modules, quiet zone included.
	 */
	public Layout layout(int matrixModules) {
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = scratch.createGraphics();
		try {
			return layout(matrixModules, graphics);
		} finally {
			graphics.dispose();
		}
	}

	private Layout layout(int matrixModules, Graphics2D graphics) {
		OnboardingProperties.Qr qr = onboardingProperties.getQr();

		// Whole-pixel modules keep the matrix edges crisp; fractional rescaling of a rasterised QR
		// is what makes branded codes hard to scan.
		int moduleScale = Math.max(1, qr.getQrSize() / matrixModules);
		int qrPx = moduleScale * matrixModules;
		int qrX = (qr.getCanvasWidth() - qrPx) / 2;

		FontMetrics wordmarkMetrics = graphics.getFontMetrics(wordmarkFont(qr));
		FontMetrics taglineMetrics = graphics.getFontMetrics(taglineFont(qr));

		int contentHeight = qrPx
			+ qr.getBrandingGap() + wordmarkMetrics.getAscent()
			+ qr.getTaglineGap() + taglineMetrics.getAscent();
		int available = qr.getCanvasHeight() - qr.getTopPadding() - qr.getBottomPadding();
		int qrY = qr.getTopPadding() + Math.max(0, (available - contentHeight) / 2);

		int wordmarkBaselineY = qrY + qrPx + qr.getBrandingGap() + wordmarkMetrics.getAscent();
		int taglineBaselineY = wordmarkBaselineY + qr.getTaglineGap() + taglineMetrics.getAscent();

		// Snapped to whole modules so the plate covers complete modules instead of clipping edges.
		int plateModules = (int) Math.floor(matrixModules * qr.getCenterLogoMaxRatio());
		int plateSize = plateModules * moduleScale;
		int plateX = qrX + (qrPx - plateSize) / 2;
		int plateY = qrY + (qrPx - plateSize) / 2;

		return new Layout(
			qr.getCanvasWidth(), qr.getCanvasHeight(),
			qrX, qrY, qrPx, moduleScale, matrixModules,
			plateX, plateY, plateSize,
			wordmarkBaselineY, taglineBaselineY
		);
	}

	private void drawQrPanel(Graphics2D graphics, BitMatrix matrix, Layout layout, OnboardingProperties.Qr qr) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(qr.qrBackgroundColorValue());
		graphics.fill(new RoundRectangle2D.Float(
			layout.qrX(), layout.qrY(), layout.qrPx(), layout.qrPx(), PANEL_CORNER_RADIUS, PANEL_CORNER_RADIUS
		));

		// Modules are filled as exact pixel squares: antialiasing here would soften module edges and
		// cost scanning margin.
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setColor(qr.qrForegroundColorValue());

		int scale = layout.moduleScale();
		for (int y = 0; y < matrix.getHeight(); y++) {
			for (int x = 0; x < matrix.getWidth(); x++) {
				if (matrix.get(x, y)) {
					graphics.fillRect(layout.qrX() + x * scale, layout.qrY() + y * scale, scale, scale);
				}
			}
		}
	}

	private void drawCenterLogo(Graphics2D graphics, Layout layout, OnboardingProperties.Qr qr) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		graphics.setColor(qr.qrBackgroundColorValue());
		graphics.fill(new RoundRectangle2D.Float(
			layout.logoPlateX(), layout.logoPlateY(), layout.logoPlateSize(), layout.logoPlateSize(),
			PLATE_CORNER_RADIUS, PLATE_CORNER_RADIUS
		));

		BufferedImage logo = brandLogoProvider.logo();
		int boxSize = layout.logoPlateSize() - 2 * qr.getCenterLogoPadding();

		// The supplied mark is never stretched: it is fitted into the padded box by its own ratio.
		double scale = Math.min(boxSize / (double) logo.getWidth(), boxSize / (double) logo.getHeight());
		int logoWidth = Math.max(1, (int) Math.round(logo.getWidth() * scale));
		int logoHeight = Math.max(1, (int) Math.round(logo.getHeight() * scale));
		int logoX = layout.logoPlateX() + (layout.logoPlateSize() - logoWidth) / 2;
		int logoY = layout.logoPlateY() + (layout.logoPlateSize() - logoHeight) / 2;

		graphics.drawImage(logo, logoX, logoY, logoWidth, logoHeight, null);
	}

	private void drawBranding(Graphics2D graphics, Layout layout, OnboardingProperties.Qr qr) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int maxTextWidth = (int) Math.round(qr.getCanvasWidth() * TEXT_MAX_WIDTH_RATIO);

		drawCenteredText(graphics, WORDMARK, wordmarkFont(qr), qr.wordmarkColorValue(),
			layout.wordmarkBaselineY(), maxTextWidth, qr.getCanvasWidth());
		drawCenteredText(graphics, qr.getTagline(), taglineFont(qr), qr.taglineColorValue(),
			layout.taglineBaselineY(), maxTextWidth, qr.getCanvasWidth());
	}

	/**
	 * Draws centred text, shrinking the font only as far as needed to keep it on one line inside the
	 * canvas.
	 */
	private void drawCenteredText(
		Graphics2D graphics, String text, Font font, Color color, int baselineY, int maxWidth, int canvasWidth
	) {
		Font fitted = font;
		FontMetrics metrics = graphics.getFontMetrics(fitted);

		while (metrics.stringWidth(text) > maxWidth && fitted.getSize() > 1) {
			fitted = fitted.deriveFont((float) fitted.getSize() - 1);
			metrics = graphics.getFontMetrics(fitted);
		}

		graphics.setFont(fitted);
		graphics.setColor(color);
		graphics.drawString(text, (canvasWidth - metrics.stringWidth(text)) / 2f, baselineY);
	}

	private Font wordmarkFont(OnboardingProperties.Qr qr) {
		// Logical sans-serif: always present in the JRE, so nothing is ever downloaded or read from
		// disk while an invitation is being created.
		return new Font(Font.SANS_SERIF, Font.BOLD, qr.getWordmarkFontSize())
			.deriveFont(Map.of(TextAttribute.TRACKING, WORDMARK_TRACKING));
	}

	private Font taglineFont(OnboardingProperties.Qr qr) {
		return new Font(Font.SANS_SERIF, Font.PLAIN, qr.getTaglineFontSize());
	}

	/**
	 * Resolved geometry of one branded card, in canvas pixels.
	 */
	public record Layout(
		int canvasWidth,
		int canvasHeight,
		int qrX,
		int qrY,
		int qrPx,
		int moduleScale,
		int matrixModules,
		int logoPlateX,
		int logoPlateY,
		int logoPlateSize,
		int wordmarkBaselineY,
		int taglineBaselineY
	) {
	}
}
