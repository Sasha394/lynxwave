package dev.client.api.nullcry.render.core.msdf.core;

import dev.client.api.nullcry.render.core.msdf.FontData.BoundsData;
import dev.client.api.nullcry.render.core.msdf.FontData.GlyphData;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

public final class MsdfGlyph {
	private final int code;
	private final float minU, maxU, minV, maxV;
	private final float advance, topPosition, width, height;
	
	public MsdfGlyph(GlyphData data, float atlasWidth, float atlasHeight) {
		this.code = data.unicode();
		this.advance = data.advance();
		
		BoundsData atlasBounds = data.atlasBounds();
		if (atlasBounds != null) {
			this.minU = atlasBounds.left() / atlasWidth;
			this.maxU = atlasBounds.right() / atlasWidth;
			this.minV = 1.0F - atlasBounds.top() / atlasHeight;
			this.maxV = 1.0F - atlasBounds.bottom() / atlasHeight;
		} else {
			this.minU = this.maxU = this.minV = this.maxV = 0.0f;
		}

		BoundsData planeBounds = data.planeBounds();
		if (planeBounds != null) {
			this.width = planeBounds.right() - planeBounds.left();
			this.height = planeBounds.top() - planeBounds.bottom();
			this.topPosition = planeBounds.top();
		} else {
			this.width = this.height = this.topPosition = 0.0f;
		}
	}
	
	public float apply(Matrix4f matrix, VertexConsumer consumer, float size, float x, float y, float z, int color) {
		return apply(matrix, consumer, size, x, y, z, color, color);
	}

	public float apply(Matrix4f matrix, VertexConsumer consumer, float size, float x, float y, float z, int leftColor, int rightColor) {
		y -= this.topPosition * size;
		float width = this.width * size;
		float height = this.height * size;
		consumer.vertex(matrix, x, y, z).texture(this.minU, this.minV).color(leftColor);
		consumer.vertex(matrix, x, y + height, z).texture(this.minU, this.maxV).color(leftColor);
		consumer.vertex(matrix, x + width, y + height, z).texture(this.maxU, this.maxV).color(rightColor);
		consumer.vertex(matrix, x + width, y, z).texture(this.maxU, this.minV).color(rightColor);
		
		return this.advance * size;
	}

	public float getWidth(float size) {
		return this.advance * size;
	}
	public float getHeight(float size) { return this.height * size; }
	public float getVisualWidth(float size) { return this.width * size; }

	public int getCharCode() {
		return code;
	}

}
