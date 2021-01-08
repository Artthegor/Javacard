package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

import com.sun.javacard.apduio.*;

import aBus.MonApplet;


public class Client {
	
	
	public static void main(String[] args) {
		
		/* Connexion a la Javacard */
		CadT1Client cad;
		Socket sckCarte;
		try {
			sckCarte = new Socket("localhost", 9025);
			sckCarte.setTcpNoDelay(true);
			BufferedInputStream input = new BufferedInputStream(sckCarte.getInputStream());
			BufferedOutputStream output = new BufferedOutputStream(sckCarte.getOutputStream());
			cad = new CadT1Client(input, output);
		} catch (Exception e) {
			System.out.println("Erreur : impossible de se connecter a la Javacard");
			return;
		}
		/* Mise sous tension de la carte */
		try {
			cad.powerUp();
		} catch (Exception e) {
			System.out.println("Erreur lors de l'envoi de la commande Powerup a la Javacard");
			try {
				sckCarte.close();
			} catch (IOException ex) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}

		/* Sélection de l'applet */
		Apdu apdu = new Apdu();
		apdu.command[Apdu.CLA] = 0x00;
		apdu.command[Apdu.INS] = (byte) 0xA4;
		apdu.command[Apdu.P1] = 0x04;
		apdu.command[Apdu.P2] = 0x00;
		byte[] appletAID = { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x00, 0x00 };
		apdu.setDataIn(appletAID);
		try {
			cad.exchangeApdu(apdu);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (CadTransportException e1) {
			System.out.println("Erreur : exchangeAPDU");
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (apdu.getStatus() != 0x9000) {
			System.out.println("Erreur lors de la sélection de l'applet");
			System.exit(1);
		}

		/* Menu principal */
		boolean fin = false;
		Scanner sc = new Scanner(System.in);
		while (!fin) {
			System.out.println();
			System.out.println("Application cliente Javacard");
			System.out.println("----------------------------");
			System.out.println();
			System.out.println("1 - Recharger le compteur");
			System.out.println("2 - Decrementer le compteur");
			System.out.println("3 - Interroger le compteur");
			System.out.println("4 - Deblocage PIN");
			System.out.println("5 - Controle");
			System.out.println("6 - Quitter");
			System.out.println();
			System.out.println("Votre choix ?");

			int choix = -1;
			try {
				choix = System.in.read();
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}

			while (!(choix >= '1' && choix <= '6')) {
				try {
					choix = System.in.read();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			apdu.command[Apdu.CLA] = MonApplet.CLA_MONAPPLET;
			apdu.command[Apdu.P1] = 0x00;
			apdu.command[Apdu.P2] = 0x00;
			switch (choix) {
				case '1':
					apdu.command[Apdu.INS] = MonApplet.INS_RECHARGER_COMPTEUR;
					byte[] donneesRec = new byte[5];
					System.out.println();
					System.out.println("Veuillez saisir le code PIN (un chiffre à la fois): ");
					donneesRec[0] = (byte)sc.nextByte();
					donneesRec[1] = (byte)sc.nextByte();
					donneesRec[2] = (byte)sc.nextByte();
					donneesRec[3] = (byte)sc.nextByte();
					System.out.println();
					System.out.println("Veuillez saisir le montant de votre recharge entre 1 et "+MonApplet.MAX_RECHARGE +": ");
					donneesRec[4] = (byte)sc.nextByte();
					apdu.setDataIn(donneesRec);
					try {
						cad.exchangeApdu(apdu);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (CadTransportException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					switch(apdu.getStatus()){
						case MonApplet.SW_ERROR_CODE:
							System.out.println("Erreur : Code erroné. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_BLOCKED:
							System.out.println("Erreur : La carte est bloquée. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_DEAD:
							System.out.println("Erreur : La carte est morte. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_AMOUNT_NEG:
							System.out.println("Erreur : Impossible de recharger avec un montant négatif. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_AMOUNT_MAX:
							System.out.println("Erreur : Impossible de recharger avec un montant au dessus de la limite. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_BALANCE_MAX:
							System.out.println("Erreur : Impossible de dépasser le montant maximum de la balance. Impossible d'effectuer un rechargement.");
							break;
						case MonApplet.SW_ERROR_CODE_BLOCKED:
							System.out.println("Erreur : Trop de tentatives la carte a été bloquée. Veuillez la débloquer. Impossible d'effectuer un rechargement.");
							break;						
						case 0x9000:
							System.out.println("Opération de rechargement validée.");
							break;
						default:
							System.out.println("Erreur : Inconnue. Impossible d'effectuer un rechargement.");
							break;
					}
					break;
	
				case '2':
					apdu.command[Apdu.INS] = MonApplet.INS_DECREMENTER_COMPTEUR;
					byte[] infoVoyage = new byte[8];
					System.out.println("Veuillez saisir le numero de la ligne : ");
					byte line = sc.nextByte();
					System.out.println("Veuillez saisir la direction : ");
					byte direction = sc.nextByte();
					System.out.println("Veuillez saisir l'annee : ");
					short year = sc.nextShort();
					System.out.println("Veuillez saisir l'heure du jour (en minutes) : ");
					short time = sc.nextShort();
					System.out.println("Veuillez saisir le mois (en nombre) : ");
					byte mounth = sc.nextByte();
					System.out.println("Veuillez saisir le jour (en nombre) : ");
					byte day = sc.nextByte();
					
					infoVoyage[0]= line;
					infoVoyage[1] = direction;
					infoVoyage[2]= (byte)(year & 0xff);
					infoVoyage[3]= (byte)((year >> 8 )&0xff);
					infoVoyage[4]= (byte)(time & 0xff);
					infoVoyage[5]= (byte)((time >> 8 )&0xff);
					infoVoyage[6] = mounth;
					infoVoyage[7] = day;
					apdu.setDataIn(infoVoyage);
					try {
						cad.exchangeApdu(apdu);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (CadTransportException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					switch(apdu.getStatus()){
						case MonApplet.SW_ERROR_BLOCKED:
							System.out.println("Erreur : La carte est bloquée. Impossible de payer pour le voyage.");
							break;
						case MonApplet.SW_ERROR_DEAD:
							System.out.println("Erreur : La carte est morte. Impossible de payer pour le voyage.");
							break;
						case MonApplet.SW_ERROR_BALANCE_NEG:
							System.out.println("Erreur : Montant insuffisant. Impossible de payer pour le voyage.");
							break;		
						case 0x9000:
							if(apdu.dataOut[0]==0){
								System.out.println("Votre carte a déjà été validée pour ce trajet.");
							}
							else{
								if(apdu.dataOut[0]<60){
									System.out.println("Corrsepondance effectuée. Il vous reste "+ apdu.dataOut[0] +" minutes avant de devoir repayer un voyage." );
								}
								else{
									System.out.println("Paiement validé.");
								}
							}
							break;
						default:
							System.out.println("Erreur : Inconnue. Impossible de payer pour le voyage.");
							break;
					}
					break;
	
				case '3':
					apdu.command[Apdu.INS] = MonApplet.INS_INTERROGER_COMPTEUR;
					byte[] donneesInt = new byte[1];
					donneesInt[0] = 0;
					apdu.setDataIn(donneesInt);
					try {
						cad.exchangeApdu(apdu);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (CadTransportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					switch(apdu.getStatus()){
						case MonApplet.SW_ERROR_BLOCKED:
							System.out.println("Erreur : La carte est bloquée. Impossible de consulter le solde.");
							break;
						case MonApplet.SW_ERROR_DEAD:
							System.out.println("Erreur : La carte est morte. Impossible de consulter le solde.");
							break;				
						case 0x9000:
							System.out.println("Valeur du compteur : " + apdu.dataOut[0]);
							break;
						default:
							System.out.println("Erreur : Inconnue. Impossible de consulter le solde.");
							break;					
					}
					break;
	
				case '4':
					apdu.command[Apdu.INS] = MonApplet.INS_DEBLOCAGE_PIN;
					byte[] CodePUK = new byte[8];
					System.out.println();
					System.out.println("Veuillez saisir le code PUK (un chiffre à la fois): ");
					CodePUK[0] = (byte)sc.nextByte();
					CodePUK[1] = (byte)sc.nextByte();
					CodePUK[2] = (byte)sc.nextByte();
					CodePUK[3] = (byte)sc.nextByte();
					CodePUK[4] = (byte)sc.nextByte();
					CodePUK[5] = (byte)sc.nextByte();
					CodePUK[6] = (byte)sc.nextByte();
					CodePUK[7] = (byte)sc.nextByte();
					apdu.setDataIn(CodePUK);
					try {
						cad.exchangeApdu(apdu);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (CadTransportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					switch(apdu.getStatus()){
						case MonApplet.SW_ERROR_CODE:
							System.out.println("Erreur : Code PUK erroné. Impossible d'effectuer le déblocage.");
							break;
						case MonApplet.SW_ERROR_BLOCKED:
							System.out.println("Erreur : La carte est bloquée. Impossible d'effectuer le déblocage.");
							break;
						case MonApplet.SW_ERROR_DEAD:
							System.out.println("Erreur : La carte est morte. Impossible d'effectuer le déblocage.");
							break;
						case MonApplet.SW_ERROR_CODE_DEAD:
							System.out.println("Erreur : Trop de tentatives la carte est morte. Impossible d'effectuer le déblocage.");
							break;				
						case 0x9000:
							System.out.println("Déblocage validé.");
							break;
						default:
							System.out.println("Erreur : Inconnue. Impossible d'effectuer le déblocage.");
							break;
					}
					break;
					
				case '5':
					apdu.command[Apdu.INS] = MonApplet.INS_CONTROL;
					byte[] infoTime = new byte[6];
					System.out.println("Veuillez saisir l'annee : ");
					short yearCon = sc.nextShort();
					System.out.println("Veuillez saisir l'heure du jour (en minutes) : ");
					short timeCon = sc.nextShort();
					System.out.println("Veuillez saisir le mois (en nombre) : ");
					byte mounthCon = sc.nextByte();
					System.out.println("Veuillez saisir le jour (en nombre) : ");
					byte dayCon = sc.nextByte();
					infoTime[0]= (byte)(yearCon & 0xff);
					infoTime[1]= (byte)((yearCon >> 8 )& 0xff);
					infoTime[2]= (byte)(timeCon & 0xff);
					infoTime[3]= (byte)((timeCon >> 8 )& 0xff);
					infoTime[4] = mounthCon;
					infoTime[5] = dayCon;
					apdu.setDataIn(infoTime);
							
					try {
						cad.exchangeApdu(apdu);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (CadTransportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					switch(apdu.getStatus()){
						case MonApplet.SW_ERROR_BLOCKED:
							System.out.println("Erreur : La carte est bloquée. Impossible de controler la carte.");
							break;
						case MonApplet.SW_ERROR_DEAD:
							System.out.println("Erreur : La carte est morte. Impossible de controler la carte.");
							break;
						case MonApplet.SW_ERROR_CONTROL_NEG:
							short timeRet = (short)(apdu.dataOut[1] << 8 | (255 & apdu.dataOut[0]));
							System.out.println("ATTENTION : Carte non validée depuis : " + timeRet + " en minutes.");
							break;
						case MonApplet.SW_ERROR_TIME_OVERFLOW:
							System.out.println("ATTENTION : Capacité dépassé. Impossible de controler la carte.");
							break;
						case 0x9000:
							System.out.println("Carte validée.");
							break;
						default:
							System.out.println("Erreur : Inconnue. Impossible de controler la carte.");
							break;					
					}
					break;
	
				case '6':
					fin = true;
				
					break;
			}
		}
		sc.close();
		/* Mise hors tension de la carte */
		try {
			cad.powerDown();
			try {
				sckCarte.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Erreur lors de l'envoi de la commande Powerdown");
			return;
		}
	}

}