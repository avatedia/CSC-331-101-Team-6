import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class Board extends JPanel{
    Image background;
    Plane c = new Plane(90, 300 );
    JLabel planeLabel;
    public Board(){
        setLayout(null);
        setFocusable(true);
        //Set Background image
        ImageIcon i =new ImageIcon ( " Skybackground.jpg");
        background = i.getImage();
// plane image
        ImageIcon j = new ImageIcon ("PlaneOne.gif" );
        planeLabel = new JLabel(j);

        planeLabel.setBounds(c.x, 300, 150, 150);
        add(planeLabel);

        addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e){
                super.keyPressed(e);
                //moving right
                if (e.getKeyCode() == 39){
                    c.x = c.x + 5;
                }
                //to move left
                else if (e.getKeyCode() == 37){
                    c.x = c.x - 5;
                }
                //Go up
                else if (e.getKeyCode() == 38){
                    c.y = c.y -5;

                }
                else if (e.getKeyCode() == 40)  {
                    c.y = c.y +5;
                }
                planeLabel.setLocation(c.x, c.y );
            }
        });
    }
    @Override
    public void paintComponent(Graphics g){
        super.paintComponent(g);
        g.drawImage(background,0,0, getWidth() ,getHeight(),this);
        

        repaint();
    }
}
