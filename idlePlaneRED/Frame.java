import javax.swing.JFrame;

public class Frame{
    public static void main (String[] args){
    Board b = new Board();
    JFrame f = new JFrame();
    f.setSize( 1024,550); 
    f.add(b);
    f.setVisible(true);
  
}
}
