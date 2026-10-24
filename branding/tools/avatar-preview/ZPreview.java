// Renders MOTION's 3D avatars without Minecraft (depth-buffered, smooth-shaded), for checking
// models and for the launcher's picker images.
//
//   C=apps/client-mod/common/src/main/java
//   javac -d /tmp/av $(find $C/dev/bloom/client/core/avatar -name '*.java') branding/tools/avatar-preview/ZPreview.java
//   java -Djava.awt.headless=true -cp /tmp/av ZPreview /tmp/avatars.png          # contact sheet
//   java -Djava.awt.headless=true -cp /tmp/av ZPreview apps/launcher/public/avatars icons
import dev.bloom.client.core.avatar.AvatarModel;
import dev.bloom.client.core.avatar.AvatarModels;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Depth-buffered, Gouraud-shaded preview (closer to what Minecraft draws than painter's sorting). */
public class ZPreview {
  static int W, H; static float[] zb; static int[] px;
  public static void main(String[] a) throws Exception {
    boolean icons = a.length > 1 && a[1].equals("icons");
    String[] ids = AvatarModels.ids().toArray(new String[0]);
    double[] views = icons ? new double[]{28} : new double[]{0, 35, 300};
    int cw = icons ? 384 : 520, ch = icons ? 512 : 520;
    for (int c = 0; c < ids.length; c++) {
      if (icons) { W = cw; H = ch; zb = new float[W*H]; px = new int[W*H]; java.util.Arrays.fill(zb, Float.MAX_VALUE); }
      else if (c == 0) { W = cw * ids.length; H = ch * views.length; zb = new float[W*H]; px = new int[W*H]; java.util.Arrays.fill(zb, Float.MAX_VALUE); java.util.Arrays.fill(px, 0xFF2A2E2C); }
      for (int r = 0; r < views.length; r++) {
        AvatarModel m = AvatarModels.get(ids[c]);
        AvatarModel.Angles ang = icons ? new AvatarModel.Angles(0.05f, -0.25f, -0.25f, 0.2f, 0.12f, -0.12f, 0)
            : r == 1 ? AvatarModel.Angles.walking(1.2f, 0.9f, 10, 25, 0, 0) : AvatarModel.Angles.REST;
        AvatarModel.Posed P = m.pose(ang); float[] q = P.geometry(); int[] cols = P.colors();
        double yaw = Math.toRadians(views[r]);
        double scale = icons ? 176 : 236;
        int ox = icons ? 0 : cw * c, oy = icons ? 0 : ch * r;
        for (int i = 0, qi = 0; i < q.length; i += 24, qi++) {
          double[][] v = new double[4][];
          for (int k = 0; k < 4; k++) {
            double x = q[i+k*6], y = q[i+k*6+1], z = q[i+k*6+2];
            double nx = q[i+k*6+3], ny = q[i+k*6+4], nz = q[i+k*6+5];
            double rx = x*Math.cos(yaw) + z*Math.sin(yaw), rz = -x*Math.sin(yaw) + z*Math.cos(yaw);
            double rnx = nx*Math.cos(yaw) + nz*Math.sin(yaw), rnz = -nx*Math.sin(yaw) + nz*Math.cos(yaw);
            // Light from the upper front-left of the camera, plus ambient.
            double lx = -0.35, ly = -0.6, lz = -0.72; // model space: y down, camera at -z
            double l = 0.42 + 0.62 * Math.max(0, rnx*lx + ny*ly + rnz*lz) + 0.12 * Math.max(0, -ny);
            int col = cols[qi*4+k];
            v[k] = new double[]{ox + cw/2.0 + rx*scale, oy + (icons ? ch - 14 - (1.5-y)*scale : 20 + (y+0.5)*scale), rz, ((col>>16)&255)*l, ((col>>8)&255)*l, (col&255)*l};
          }
          tri(v[0], v[1], v[2], ox, oy, cw, ch); tri(v[0], v[2], v[3], ox, oy, cw, ch);
        }
      }
      if (icons) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); img.setRGB(0,0,W,H,px,0,W);
        BufferedImage small = new BufferedImage(W/2, H/2, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = small.createGraphics(); g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, W/2, H/2, null); ImageIO.write(small, "png", new File(a[0], ids[c] + ".png"));
      }
    }
    if (!icons) { BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); img.setRGB(0,0,W,H,px,0,W); ImageIO.write(img, "png", new File(a[0])); }
  }
  static void tri(double[] a, double[] b, double[] c, int ox, int oy, int cw, int ch) {
    int minX = (int)Math.max(ox, Math.floor(Math.min(a[0], Math.min(b[0], c[0])))), maxX = (int)Math.min(ox+cw-1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
    int minY = (int)Math.max(oy, Math.floor(Math.min(a[1], Math.min(b[1], c[1])))), maxY = (int)Math.min(oy+ch-1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
    double area = (b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0]);
    if (Math.abs(area) < 1e-9) return;
    for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
      double pxx = x + 0.5, pyy = y + 0.5;
      double w0 = ((b[0]-pxx)*(c[1]-pyy) - (b[1]-pyy)*(c[0]-pxx)) / area;
      double w1 = ((c[0]-pxx)*(a[1]-pyy) - (c[1]-pyy)*(a[0]-pxx)) / area;
      double w2 = 1 - w0 - w1;
      if (w0 < 0 || w1 < 0 || w2 < 0) continue;
      float z = (float)(w0*a[2] + w1*b[2] + w2*c[2]);
      int idx = y*W + x;
      if (z >= zb[idx]) continue;
      zb[idx] = z;
      int r = (int)Math.min(255, w0*a[3]+w1*b[3]+w2*c[3]), g = (int)Math.min(255, w0*a[4]+w1*b[4]+w2*c[4]), bb = (int)Math.min(255, w0*a[5]+w1*b[5]+w2*c[5]);
      px[idx] = 0xFF000000 | (r<<16) | (g<<8) | bb;
    }
  }
}
