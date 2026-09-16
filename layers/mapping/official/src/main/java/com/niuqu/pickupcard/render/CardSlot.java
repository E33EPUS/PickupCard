package com.niuqu.pickupcard.render;

/**
 * 一张卡这一帧要画在哪、多大。位置由 {@code shared} 里的排布数学算好，
 * painter 拿到就是最终屏幕坐标。
 */
public record CardSlot(CardView view, float x, float y, float width, float height) {

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    /** 卡身中心。入场/退场的缩放变换绕它转。 */
    public float centerX() {
        return x + width / 2f;
    }

    public float centerY() {
        return y + height / 2f;
    }
}
