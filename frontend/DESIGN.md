---
name: Kinship & Comfort
colors:
  surface: "#f9f9f9"
  surface-dim: "#dadada"
  surface-bright: "#f9f9f9"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f3f3f3"
  surface-container: "#eeeeee"
  surface-container-high: "#e8e8e8"
  surface-container-highest: "#e2e2e2"
  on-surface: "#1a1c1c"
  on-surface-variant: "#444650"
  inverse-surface: "#2f3131"
  inverse-on-surface: "#f1f1f1"
  outline: "#747781"
  outline-variant: "#c4c6d2"
  surface-tint: "#3f5c9f"
  primary: "#001d4f"
  on-primary: "#ffffff"
  primary-container: "#0c3273"
  on-primary-container: "#809de4"
  inverse-primary: "#b0c6ff"
  secondary: "#006a65"
  on-secondary: "#ffffff"
  secondary-container: "#77f3ea"
  on-secondary-container: "#006f69"
  tertiary: "#001d4e"
  on-tertiary: "#ffffff"
  tertiary-container: "#003179"
  on-tertiary-container: "#6f9bff"
  error: "#ba1a1a"
  on-error: "#ffffff"
  error-container: "#ffdad6"
  on-error-container: "#93000a"
  primary-fixed: "#d9e2ff"
  primary-fixed-dim: "#b0c6ff"
  on-primary-fixed: "#001945"
  on-primary-fixed-variant: "#244485"
  secondary-fixed: "#7af6ed"
  secondary-fixed-dim: "#5adad1"
  on-secondary-fixed: "#00201e"
  on-secondary-fixed-variant: "#00504c"
  tertiary-fixed: "#d9e2ff"
  tertiary-fixed-dim: "#b0c6ff"
  on-tertiary-fixed: "#001945"
  on-tertiary-fixed-variant: "#00419c"
  background: "#f9f9f9"
  on-background: "#1a1c1c"
  surface-variant: "#e2e2e2"
  warm-bg: "#FAF9F6"
  border-gray: "#E2E2E4"
  text-muted: "#8E8E93"
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: "700"
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: "700"
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: "700"
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: "600"
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: "400"
    lineHeight: 28px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: "400"
    lineHeight: 24px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: "400"
    lineHeight: 20px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: "600"
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: "500"
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 8px
  sm: 16px
  md: 24px
  lg: 40px
  xl: 64px
  gutter: 20px
  margin-mobile: 16px
  margin-desktop: auto
  max-width: 1200px
---

## Brand & Style

The design system is built for a university dormitory roommate matching platform, where the core values are reliability, approachability, and clarity. The brand personality is "Bright, Friendly, and Trustworthy," aiming to reduce the anxiety of finding a co-living partner.

The chosen style is **Modern Professional with Soft-Tactile touches**. It avoids the sterility of pure corporate SaaS by using a warm background palette and generous whitespace, while maintaining a structured information hierarchy. The interface prioritizes high legibility and "breathable" layouts to help students process complex personal preferences without feeling overwhelmed.

Key visual principles:

- **Clarity over Decoration:** No unnecessary ornaments; every element serves a functional purpose in the matching process.
- **Friendly Precision:** Softened geometry (12-16px radii) paired with a disciplined grid.
- **Warm Neutrality:** Replacing stark whites with ivory and warm grays to create a more inviting "home" feel.

## Colors

The palette is anchored by a deep **Primary Navy (#0C3273)** which establishes trust and institutional authority, essential for a university-affiliated service. The **Point Mint (#00A79F)** acts as a refreshing secondary color, used for success states, active matching indicators, and call-to-action highlights.

The background uses a **Warm Ivory (#FAF9F6)** instead of pure white to reduce eye strain and enhance the "homey" dormitory vibe. **Tertiary Blue (#3478F6)** is reserved for interactive utility links and informational icons.

Neutral tones are carefully stepped:

- **Surface:** Warm Ivory for the main canvas.
- **Container:** Pure White (#FFFFFF) for elevated cards.
- **Borders:** Low-contrast gray (#E2E2E4) to define sections without creating visual noise.

## Typography

This design system utilizes **Plus Jakarta Sans** (a modern alternative to Pretendard that offers a slightly more friendly, open geometric feel). The type scale is optimized for reading dense "roommate profile" data.

- **Headlines:** Use Bold weights with tighter letter spacing for a confident, grounded look.
- **Body Text:** Standardized at 16px for desktop and 14px for secondary information to ensure accessibility.
- **Labels:** Used for tags (e.g., "Non-smoker," "Early bird") with slightly increased letter spacing and semi-bold weights for quick scanning.
- **Mobile Adjustments:** Large headlines scale down significantly on mobile to prevent awkward text wrapping in narrow profile cards.

## Layout & Spacing

The design system employs a **Fixed-Fluid Hybrid Grid**. On desktop, content is centered within a 1200px container. On mobile, it transitions to a fluid 4-column layout with 16px side margins.

- **Spacing Rhythm:** Based on an 8px scale. Use 16px (sm) for internal element padding and 24px (md) for spacing between logical sections.
- **Profile Cards:** In the roommate marketplace, cards should use a 2-column grid on tablet and 1-column on mobile.
- **Whitespace:** Emphasize vertical rhythm. Allow for 40px - 64px of space between major sections to prevent the "cluttered dormitory" feel visually.

## Elevation & Depth

Depth in this design system is created through **Tonal Layering** and **Subtle Ambient Shadows**.

1.  **Level 0 (Base):** Warm Ivory (#FAF9F6) - The canvas.
2.  **Level 1 (Cards/Surface):** Pure White (#FFFFFF) with a very soft shadow: `0px 4px 12px rgba(12, 50, 115, 0.05)`. Note the slight Navy tint in the shadow to maintain color harmony.
3.  **Level 2 (Interactive/Hover):** Increase shadow spread and slightly lift the element: `0px 8px 20px rgba(12, 50, 115, 0.08)`.
4.  **Overlays:** Use a 40% opacity Navy backdrop for modals to keep the focus sharp on the foreground content.

Avoid heavy black shadows or high-contrast borders. Depth should feel natural and light.

## Shapes

The shape language is defined by **Soft Geometric Rounds**.

- **Standard Cards & Containers:** 16px corner radius to evoke a friendly, modern feel.
- **Buttons:** 12px corner radius (slightly sharper than cards to imply action).
- **Chips/Tags:** Fully rounded (pill-shaped) to distinguish them from interactive buttons.
- **Form Inputs:** 12px radius to match buttons, creating a cohesive "action group" aesthetic.

## Components

### Buttons

- **Primary:** Solid Navy (#0C3273) with White text. Bold, authoritative.
- **Secondary:** Outline Mint (#00A79F) with Mint text and 1.5px border. Used for "Message" or "View Profile" actions.
- **Ghost:** Transparent background with Muted Gray text for "Cancel" or "Skip" actions.

### Profile Cards

The core component of the platform. Features a 16px rounded white surface, a 1:1 aspect ratio avatar with 12px radius, and "Match Score" displayed as a Mint-colored badge in the top right. Detailed preferences (Smoking, Sleep pattern) are displayed as small, low-contrast gray chips.

### Form Inputs

Standardized with a 1px border (#E2E2E4). On focus, the border shifts to Point Mint (#00A79F) with a subtle glow. Labels sit above the input in `label-md` style.

### Navigation

A clean top bar with the logo on the left and primary navigation links in the center. Use a "Profile" icon or avatar on the far right. The navigation bar should have a very subtle bottom border (#E2E2E4) rather than a shadow to keep the header feeling light.

### Roommate Tags (Chips)

Small, rounded-full elements. Background: #F6F6F6; Text: #8E8E93. For "Verified Student" tags, use a light Mint background with dark Mint text to signify trust.
