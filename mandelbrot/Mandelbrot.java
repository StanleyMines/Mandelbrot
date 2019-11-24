package mandelbrot;

import val.Q;
import val.QP;
import val.primativedouble.Value;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static val.Q.*;
import static val.QP.qp;
import static val.primativedouble.Value.FOUR;
import static val.primativedouble.Value.val;

public class Mandelbrot
{
  public static double COLORRATE = 1D/8D;
  public static final int TILESIZE = 150; // 150
  public static final int DEPTH = 8*2048; // 2048  64
  public static final int ANTIALIASING = 1; // 1
  public static final int MAXTHREADS = 4*8+1;
  public static final double ZOOMSCALE = 1D/8;

  private volatile Integer threadCount = 0; private synchronized int getThreads(){ return threadCount;} private synchronized void addThread(){ if (threadCount >=MAXTHREADS) System.err.println("Attempting to create a thread exceeding thread limit!"); threadCount++;} private synchronized void remThread(){ threadCount--;} private synchronized boolean canStartNewThread(){return threadCount<MAXTHREADS;}
  private volatile List<Thread> threads = Collections.synchronizedList(new ArrayList<Thread>(MAXTHREADS));
  HashMap<HashableView, Color[]> calculated = new HashMap<>(20);
  Display.WindowSize display;
  Display.WindowSize actual = new Display.WindowSize(3840,2160);
  Display.WindowSize use;

  // Original
//  double scale = -7.5;
//  QP center = qp(q(-1, 2), q(0, 1));

  // ??
  //double scale = -20.5;
  //QP center = qp(q(81448, 49550959), q(-24170803,29388151));

  // 00 Cool spikes
  //double scale = -19.5;
  //QP center = qp(new Q(new BigInteger("-2301783278655076632811585394649140808770420500066574794752"),new BigInteger("15459423034510202420813247184330822928167207450548633600000")),new Q(new BigInteger("-7929977679099751430849043327089856342822712822632586149888"),new BigInteger("7729711517255101210406623592165411464083603725274316800000")));

  // 01 Arcing Zoom
  //double scale = -29.0;
  //QP center = qp(new Q(new BigInteger("-85241514145487948761243"), new BigInteger("572479338973652582400000")), new Q(new BigInteger("-7047730329760827785407283"), new BigInteger("6869752067683830988800000")));

  // 02
  //double scale = -45.0;
  double scale = -41;
  QP center = qp(new Q(new BigInteger("-824405337713456342058634333124188673418821894144"), new BigInteger("5536680432649312569992785998100296189031219200000")), new Q(new BigInteger("-68161465287625812021748692727096276126856290238464"), new BigInteger("66440165191791750839913431977203554268374630400000")));

  //Now centered on (-0.1488988479182952 + -1.0259075228224855 * i) (-156351065007566805536679717902520180585184297051059600526282234912293357272305181740113695589501876635357821471771652298663040820339068108800000/1050048856612784811624118896102900188226486902730961598736571103158496705677236567292605696792464875546003256803410319985501278977392640000000000 + i * -12927036255962464080151345488248209469761598172246797660221691866872273692602486962073929305996030740650276902171941681540968122573928372633600000/12600586279353417739489426753234802258717842832771539184838853237901960468126838807511268361509578506552039081640923839826015347728711680000000000)
  //double scale = -55.0;
  //QP center = qp(new Q(new BigInteger("-156351065007566805536679717902520180585184297051059600526282234912293357272305181740113695589501876635357821471771652298663040820339068108800000"), new BigInteger("1050048856612784811624118896102900188226486902730961598736571103158496705677236567292605696792464875546003256803410319985501278977392640000000000")), new Q(new BigInteger("-12927036255962464080151345488248209469761598172246797660221691866872273692602486962073929305996030740650276902171941681540968122573928372633600000"), new BigInteger("12600586279353417739489426753234802258717842832771539184838853237901960468126838807511268361509578506552039081640923839826015347728711680000000000")));

  // ## name
  //double scale = -13.5;
  //QP center = qp(new Q(new BigInteger(""), new BigInteger("")), new Q(new BigInteger(""), new BigInteger("")));

//  double scale = -49.5;
//  QP center = qp(new Q(new BigInteger("-19854057535354568"),BigInteger.TEN.pow(16)),new Q(new BigInteger("-00000260443807927"),BigInteger.TEN.pow(16)));


  public static void main(String[] args)
  {
    if (args.length!=0)
      Headless.headless(args);
    else
      new Display().run();
  }

  public void doClick()
  {
    Point pt = Display.getCursorLocationOrigin(display);
    Q scaleFactor = srot(scale);
    center = qp(center.x.a(q(pt.x - display.w / 2).m(scaleFactor)), center.y.s(q(display.h / 2 - pt.y).m(scaleFactor)));

    clearThreads();
    calculated.clear();

    System.out.printf("%sNow centered on (%s + %s * i) (%s/%s + i * %s/%s)%s", "\n", center.x, center.y, center.x.n, center.x.d, center.y.n, center.y.d,"\n");
    s = false;
    loop = false;
  }

  private static boolean s = false;
  private static boolean loop = false;
  private static boolean notice = false;
  private static int loopindex = 0;
  public void keyPress(int key, int action)
  {
    if (action==GLFW_RELEASE)
    {
      // Plus -> Zoom In
      if (key == GLFW_KEY_EQUAL || key == GLFW_KEY_KP_ADD)
        zoom(-ZOOMSCALE);
      // Minus -> Zoom Out
      if (key == GLFW_KEY_MINUS|| key == GLFW_KEY_KP_SUBTRACT)
        zoom(ZOOMSCALE);
      // S -> Save Image
      if (key== GLFW_KEY_S)
      {
        s = true;
        String filename = ("render/"+"MandelbrotRender"+"t"+System.currentTimeMillis()/1000)+"_"+center.x+"+"+center.y+"i"+"_"+"Zoom"+scale+"_"+"CLR"+COLORRATE+"_"+"DPTH"+DEPTH+"_"+"AA"+ANTIALIASING+".png";
        Headless.saveImage(filename, use.w, use.h,precalculated);
      }

      String latestFile = "last.mandel";
      // W -> Write View Parameters To File
      if (key==GLFW_KEY_W)
      {
        System.out.println("Starting text file location save at: "+latestFile);
        try {
          FileWriter fw = new FileWriter(latestFile);
          fw.write(scale+"\n");
          fw.write(center.x+"\n");
          fw.write(center.y.toString());
          fw.close();
          System.out.println("Location data saved as text.");
        } catch (IOException e)
        {
          System.err.println("Unable to save location.");
        }
      }
      // R -> Read View Parameters From File
      if (key==GLFW_KEY_R)
      {
        loop = false;
        s = false;
        try {
          Scanner scanner = new Scanner(new File(latestFile));
          scale=new Double(scanner.nextLine());
          center = qp(q(scanner.nextLine()),q(scanner.nextLine()));
          clearThreads();
          calculated.clear();
          System.out.printf("%sNow centered on (%s + %s * i) (%s/%s + i * %s/%s)%s", "\n", center.x, center.y, center.x.n, center.x.d, center.y.n, center.y.d,"\n");
          zoom(0);
        } catch (IOException e)
        {
          System.err.println("Unable to save location.");
        }
      }
      // L -> Start Render Loop
      // See S. If last action was save, loop will save at each iteration.
      if (key==GLFW_KEY_L)
      {
        loop = !loop;
        loopindex = 0;
      }
      // Z -> Zoom Reset (Zooms out, or if all the way zoomed out, zoom all the way in.)
      if (key==GLFW_KEY_Z)
      {
        if (scale!=-7.5)
          scale = -7.5;
        else
          scale = -50;
        zoom(0);
      }
      if (key==GLFW_KEY_V)
      {
        if (use==display)
          use = actual;
        else
          use = display;
      }

      if (key!=GLFW_KEY_S && key!=GLFW_KEY_L)
      {
        s = false;
      }
    }
  }

  private void zoom(double zoomscale)
  {
    scale += zoomscale;
    clearThreads();
    System.out.println("The scale is now: " + scale + " (Pixel Size: "+srot(scale)+")");
  }

  Color[] precalculated = null;
  long startTime;
  public void display()
  {
    if (!Display.w.equals(this.display))
    {
      if (use==display) {
        calculated.clear();
        clearThreads();
      }
      this.display = Display.w;
    }
    if (use==null)
      use=display;
    HashableView hv = new HashableView(use, center, scale);
    int hvhc = hv.hashCode();

    precalculated = null;
    for (HashableView h : calculated.keySet())
      if (h.hashCode() == hvhc)
        precalculated = calculated.get(h);

    synchronized (threads)
    {
      ArrayList<Thread> toRem = new ArrayList<>();
      for (Thread thrd : threads)
        if (!thrd.isAlive())
        {
          toRem.add(thrd);
        }
      for (Thread thrd : toRem)
      {
        thrd.interrupt();
        threads.remove(thrd);
        remThread();
      }
    }

    if (precalculated == null || (getThreads()==0 && cont(precalculated,null))) {
      startTime=System.currentTimeMillis();
      calculate(hv);
    }
    else
      display(precalculated); //TODO
    if (precalculated!=null && !cont(precalculated,null)) {
      if(!notice)
      {
        System.out.println("Done! (frame complete in "+(System.currentTimeMillis()-startTime)/1000D+"s)");
        notice = true;
      }
      if (loop) {
        if (s) {
          String filename = ("render/series/MandelbrotRender"+(loopindex++)+".png");
          Headless.saveImage(filename, display.w, display.h, precalculated);
          calculated.clear();
        }
        zoom(-ZOOMSCALE);
      }
    }
    else
      notice = false;
  }

  public static boolean cont (Object[] data, Object value)
  {
    for (Object o : data)
      if (o==value)
        return true;
    return false;
  }

  // TODO
  public void display(Color[] data)
  {
    glPointSize(5f);
    //float mod = Math.min((float)display.w/use.w,(float)display.w/use.w);
    for (int y = 0; y < display.h; y++)
      for (int x = 0; x < display.w; x++)
      {
        if (use.w * y + x >= data.length)
          continue;
        Color c = data[use.w * y + x];
        if (c != null)
          Display.setColor3(c);
        else
          glColor3f(.125f, .125f, .125f);
        glBegin(GL_POINTS);
        Display.doPointOr(x, y);
        glEnd();
      }
  }

  public void calculate(HashableView hv)
  {
    Color[] clrset = new Color[hv.size.w*hv.size.h];
    calculated.put(hv, clrset);
    Thread runner = new Thread(()->
    {
      Q scaleFactor = srot(scale);
      Q scaleFactorSmall = scaleFactor.m(q(1,ANTIALIASING));
      HashSet<int[]> tiles = new HashSet<>();
      for (int y = -hv.size.h/2; y < hv.size.h/2; y+=TILESIZE)
        for (int x = -hv.size.w/2; x < hv.size.w/2; x+=TILESIZE)
        {
          int[] toPut = new int[]{x, y, Display.normalizeInt(x + TILESIZE - 1, -hv.size.w / 2, hv.size.w / 2 - 1), Display.normalizeInt(y + TILESIZE - 1, -hv.size.h / 2, hv.size.h / 2 - 1)};
          tiles.add(toPut);
        }
      Iterator<int[]> tileSet= tiles.iterator();
      while (tileSet.hasNext())
      {
        if (canStartNewThread())
        {
          final int[] workTile = tileSet.next();
          Thread worker = new Thread(() ->
          {
            for (int y = workTile[1]; y <= workTile[3]; y++)
            {
              Q qy = (hv.pt.y.a(scaleFactor.m(y)));
              int yPositionOffset = (y + hv.size.h / 2) * hv.size.w + hv.size.w / 2;
              for (int x = workTile[0]; x <= workTile[2]; x++)
              {
                Q qx = hv.pt.x.a(scaleFactor.m(x));
                long steps = -1;
                int rate = ANTIALIASING*ANTIALIASING;
                Color clrave = Color.BLACK;
                try {
                  long[] d = new long[ANTIALIASING*ANTIALIASING];
                  for (int y2 = 0; y2 < ANTIALIASING; y2++)
                    for (int x2 = 0; x2 < ANTIALIASING; x2++)
                      // Decimal Type
                      d[y2*ANTIALIASING+x2] = countStepsValue(val(qx.a(scaleFactorSmall.m(x2)).toString()), val(qy.a(scaleFactorSmall.m(y2)).toString()), DEPTH);
                  // Quotient Type
                  //d[y2*ANTIALIASING+x2] = countStepsValue(val(qx.n,qx.d), val(qy.n,qy.d), DEPTH);

                  int red = 0, green = 0, blue = 0;
                  for (int i = 0; i < ANTIALIASING*ANTIALIASING; i++)
                    if (d[i] != -1) {
                      Color loc = Display.hsb4ToColor((int)(d[i]%360L), 90, rate*90, 255);
                      red += loc.getRed();
                      green += loc.getGreen();
                      blue += loc.getBlue();
                    }
                  clrave = new Color(red/(ANTIALIASING*ANTIALIASING),green/(ANTIALIASING*ANTIALIASING),blue/(ANTIALIASING*ANTIALIASING));
                } catch (Exception e)
                {
                  System.out.println("{"+qx.toString()+","+qy.toString()+"}");
                }
                synchronized (clrset)
                {
                  clrset[yPositionOffset+x] = clrave;
                  //if (steps == -1)
                  //  clrset[yPositionOffset + x] = Color.BLACK;
                  //else
                  //  clrset[yPositionOffset + x] = Display.hsb4ToColor((int)steps, 90, rate*90, 255);
                }
              }
            }
          });
          synchronized (threads)
          {
            threads.add(worker);
          }
          worker.start();
          addThread();
        }
        else
          try { Thread.sleep(50); } catch (InterruptedException e) {}
      }
    });

    synchronized (threads)
    {
      threads.add(runner);
    }
    runner.start();
    addThread();
  }

  public static long countStepsSimple(double cx, double cy, long maxSteps)
  {
    double zx = 0, zy = 0;
    double temp = 0;

    double zxS = zx * zx, zyS = zy * zy;

    long steps = 0;
    while (steps < maxSteps && zxS + zyS < 4)
    {
      temp = zxS - zyS + cx;
      zy = sq(zx + zy) - zxS - zyS + cy;
      zx = temp;
      steps++;
      zxS = zx * zx;
      zyS = zy * zy;
    }
    if (zxS + zyS < 4)
      steps++;
    return steps == maxSteps + 1 ? -1 : steps;
  }
  public static long countStepsValue(Value cx, Value cy, long maxSteps)
  {
    Value zx = Value.ZERO, zy = Value.ZERO;
    Value temp;

    Value zxS = zx.mul(zx), zyS = zy.mul(zy);

    long steps = 0;
    while (steps < maxSteps && zxS .add (zyS) .lessThan (FOUR))
    {
      temp = zxS .sub (zyS) .add (cx);
      zy = (zx .add (zy)).sq() .sub (zxS) .sub (zyS) .add (cy);
      zx = temp;
      steps++;
      zxS = zx.mul(zx);
      zyS = zy.mul(zy);
    }
    if (zxS .add (zyS) .lessThan (FOUR))
      steps++;
    return steps == maxSteps + 1 ? -1 : steps;
  }

  public void clearThreads()
  {
    if (getThreads()!=0)
    {
      synchronized (threads)
      {
        for (Thread t: threads)
        {
          t.interrupt();
          t.stop();
          remThread();
        }
        threads.clear();
      }
    }
  }

  public static double sq(double x)
  {
    return x*x;
  }

  public static class HashableView
  {
    Display.WindowSize size;
    QP pt;
    double scl;

    public HashableView(Display.WindowSize size, QP point, double scale)
    {
      this.size = size;
      pt = point;
      scl = scale;
    }

    public int hashCode()
    {
      return Arrays.hashCode(new Object[]{size, pt, scl});
      //return new Integer(pt.hashCode() ^ ((Double) scl).hashCode()).hashCode();
    }
  }
}
