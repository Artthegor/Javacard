package aBus;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;

public class MonApplet extends Applet {


	/* Constantes */
	public static final byte CLA_MONAPPLET = ( byte)  0x25;

	public static final byte INS_RECHARGER_COMPTEUR = 0x00;
	public static final byte INS_DECREMENTER_COMPTEUR = 0x01;
	public static final byte INS_INTERROGER_COMPTEUR = 0x02;
	public static final byte INS_CONTROL = 0x04;
	public static final byte INS_DEBLOCAGE_PIN = 0x03;
	public static final byte INS_VERIFICATION_CODE = 0x05;


	public static final byte SW_ERROR_DEFAULT = 0x0000;
	public static final byte SW_ERROR_CODE = 0x0001;
	public static final byte SW_ERROR_BLOCKED = 0x0002;
	public static final byte SW_ERROR_DEAD = 0x0003;
	public static final byte SW_ERROR_USE = 0x0004;
	public static final byte SW_ERROR_TIME_OVERFLOW = 0x0005;

	public static final byte SW_ERROR_AMOUNT_NEG = 0x11;
	public static final byte SW_ERROR_AMOUNT_MAX = 0x12;
	public static final byte SW_ERROR_BALANCE_MAX = 0x13;
	public static final byte SW_ERROR_CODE_BLOCKED = 0x14;

	public static final byte SW_ERROR_BALANCE_NEG = 0x21;
	
	public static final byte SW_ERROR_CONTROL_NEG = 0x31;
	
	public static final byte SW_ERROR_CODE_DEAD = 0x41;


	public static final byte PIN_LIMIT = 3;
	public static final byte PIN_SIZE = 4;
	public static final byte PUK_LIMIT = 4;
	public static final byte PUK_SIZE = 8;

	public static final byte MAX_COMPTEUR = 50;
	public static final byte VALID_DURATION = 60;
	public static final byte MAX_RECHARGE = 10;

	public static final short LCS_USE = 0;
	public static final short LCS_BLOCKED = 1;
	public static final short LCS_DEAD = -1;

	/* Attributs */
	private byte balance, day, month, line;
	private OwnerPIN PIN, PUK;
	private short year, time;
	private boolean direction;


	private short lifeCycleState;

	private MonApplet() {
		balance = 0;
		day = 1;
		month = 12;
		line = 0;
		year = 2020;
		time = 23*60+59;
		direction = false;
		PIN = new OwnerPIN(PIN_LIMIT, PIN_SIZE);
		byte [] codePIN = {1, 2, 3, 4};
		PIN.update(codePIN,(short)0,PIN_SIZE);
		PUK = new OwnerPIN(PUK_LIMIT,PUK_SIZE);
		byte [] codePUK = {1, 2, 3, 4,5,6,7,8};
		PUK.update(codePUK, (short)0, PUK_SIZE);
		lifeCycleState = LCS_USE;
	}

	public short getMinutesLastUse(short year, byte mounth, byte day, short time){
		return (short) ((((year-this.year)*12+(short)(mounth-this.month))*31+(short)(day-this.day))*1440+(time-this.time));
	}

	public short validationVoyage(byte line, boolean direction, short year, short time, byte mounth, byte day){
		if(lifeCycleState == LCS_BLOCKED) {
			return -2;
		}
		if(lifeCycleState == LCS_DEAD) {
			return -3;
		}
		int lastTime = getMinutesLastUse(year,mounth,day,time);
		if(lastTime < VALID_DURATION){
			if(line == this.line && direction == this.direction){
				return 0;
			}
			else{
				if(lastTime>=0){
					this.line = line;
					this.direction = direction;
					return (short)(VALID_DURATION - lastTime);
				}
				else{
					if(balance < 1){
						//error pas assez de voyage

						return -21;
					}
					else{
						this.year = year;
						this.month = mounth;
						this.day = day;
						this.time = time;
						this.line = line;
						this.direction = direction;
						balance--;
						return VALID_DURATION;
					}
				}
			}
		}
		else{
			if(balance < 1){
				//error pas assez de voyage

				return -21;
			}
			else{
				this.year = year;
				this.month = mounth;
				this.day = day;
				this.time = time;
				this.line = line;
				this.direction = direction;
				balance--;
				return VALID_DURATION;
			}
		}

	}

	public int control(short year, short time, byte month, byte day){
		if(lifeCycleState == LCS_BLOCKED) {
			return -2;
		}
		if(lifeCycleState == LCS_DEAD) {
			return -3;
		}
		int lastTime = getMinutesLastUse(year,month,day,time);
		if(lastTime < VALID_DURATION){
			if(lastTime>=0){
				return 0;
			}
			else{
				return -5;
			}
		}
		else{
			return (lastTime-VALID_DURATION);
		}

	}

	public boolean checkCode(OwnerPIN Password,byte [] code, boolean isPINCode){
		byte length;
		if (isPINCode){
			length = PIN_SIZE;
		}
		else{
			length = PUK_SIZE;
		}
		Password.check(code, (short)0, length);
		return Password.isValidated();
	}

	public byte deblocage(byte[] Password){
		if(lifeCycleState == LCS_USE) {
			return -4;
		}
		if(lifeCycleState == LCS_DEAD) {
			return -3;
		}
		boolean identified = true;
		identified = checkCode(PUK, Password, false);
		if(identified){
			this.lifeCycleState = LCS_USE;
			this.PIN.resetAndUnblock();
			this.PUK.reset();
		}
		else{
			if(PUK.getTriesRemaining() == 0){
				lifeCycleState = LCS_DEAD;
				return -41;
			}
			return -1;
		}

		return 0;
	}

	public byte rechargement(byte amount,byte[] Password){
		if(lifeCycleState == LCS_BLOCKED) {
			return -2;
		}
		if(lifeCycleState == LCS_DEAD) {
			return -3;
		}
		boolean identified = true;
		identified = checkCode(PIN, Password, true);
		if(identified){
			this.PIN.reset();
		}
		else{
			if(PIN.getTriesRemaining() == 0){
				lifeCycleState = LCS_BLOCKED;
				return -14;
			}
			return -1;
		}
		if(amount <= 0 ){
			return -11;
		}
		if (amount > MAX_RECHARGE){
			return -12;
		}
		if(amount+balance > MAX_COMPTEUR){
			return -13;
		}
		this.balance += amount;

		return balance;
	}



	public static void install(byte bArray[], short bOffset, byte bLength)
			throws ISOException {
		new MonApplet().register();
	}


	public void process(APDU apdu) throws ISOException {
		// TODO Auto-generated method stub
		byte[] buffer = apdu.getBuffer();
		if ( this.selectingApplet()) return;
		if (buffer[ISO7816.OFFSET_CLA] != CLA_MONAPPLET) {   
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}


		switch (buffer[ISO7816.OFFSET_INS]) {
			case INS_RECHARGER_COMPTEUR:    
				byte [] codePIN = new byte[4];
				apdu.setIncomingAndReceive();
				 byte rechargeAmount = buffer[ISO7816.OFFSET_CDATA+ISO7816.OFFSET_LC];
				 for(int i = 0; i< 4;i++)
					 codePIN[i] = buffer[ISO7816.OFFSET_CDATA+i];
				byte resRec = rechargement(rechargeAmount,codePIN);
				if(resRec<0){
					switch(resRec){
						case -1: 
							resRec = SW_ERROR_CODE;
							break;
						case -2: 
							resRec = SW_ERROR_BLOCKED;
							break;
						case -3:
							resRec = SW_ERROR_DEAD;
							break;
						case -11:
							resRec = SW_ERROR_AMOUNT_NEG;
							break;
						case -12:
							resRec = SW_ERROR_AMOUNT_MAX;
							break;
						case -13: 
							resRec = SW_ERROR_BALANCE_MAX;
							break;
						case -14:
							resRec = SW_ERROR_CODE_BLOCKED;
							break;
						default:
							resRec =  SW_ERROR_DEFAULT;
							break;
					}
					ISOException.throwIt(resRec);
				}
				break;
			case INS_DECREMENTER_COMPTEUR:    
				apdu.setIncomingAndReceive();
				boolean direction = buffer[ISO7816.OFFSET_CDATA+1]==1;
				byte line = buffer[ISO7816.OFFSET_CDATA];
				short year = (short)(buffer[ISO7816.OFFSET_CDATA+3] << 8 | (255 & buffer[ISO7816.OFFSET_CDATA+2]));
				short time = (short)(buffer[ISO7816.OFFSET_CDATA+5] << 8 | (255 & buffer[ISO7816.OFFSET_CDATA+4]));
				byte mounth = buffer[ISO7816.OFFSET_CDATA+6];
				byte day = buffer[ISO7816.OFFSET_CDATA+7];
				int resDec = validationVoyage(line,direction,year,time,mounth,day);
				short retour = 0;
				if(resDec<0){
					switch(resDec){
						case -2: 
							retour = SW_ERROR_BLOCKED;
							break;
						case -3:
							retour = SW_ERROR_DEAD;
							break;
						case -21:
							retour = SW_ERROR_BALANCE_NEG;
							break;
						default:
							retour =  SW_ERROR_DEFAULT;
							break;
					}
					ISOException.throwIt(retour);
				}
				else{
					buffer[0]  = (byte) resDec;
					apdu.setOutgoingAndSend((short)  0, (  short)  1);
				}
				break;
			case INS_INTERROGER_COMPTEUR:
				if(lifeCycleState == LCS_BLOCKED) {
					ISOException.throwIt(SW_ERROR_BLOCKED);
				}
				if(lifeCycleState == LCS_DEAD) {
					ISOException.throwIt(SW_ERROR_DEAD);
				}
				buffer[0]  = balance;
				apdu.setOutgoingAndSend((short)  0, (  short)  1);
				break;
			case INS_DEBLOCAGE_PIN:
				byte [] codePUK = new byte[8];
				apdu.setIncomingAndReceive();
				for(int i = 0; i< 8;i++)
					 codePUK[i] = buffer[ISO7816.OFFSET_CDATA+i];
				byte resDeb = deblocage(codePUK);
				if(resDeb<0){
					switch(resDeb){
						case -1: 
							resDeb = SW_ERROR_CODE;
							break;
						case -4: 
							resDeb = SW_ERROR_USE;
							break;
						case -3:
							resDeb = SW_ERROR_DEAD;
							break;
						case -41:
							resDeb = SW_ERROR_CODE_DEAD;
							break;
						default:
							resDeb =  SW_ERROR_DEFAULT;
							break;
					}
					ISOException.throwIt(resDeb);
				}
				break;
			case INS_CONTROL:
				apdu.setIncomingAndReceive();
				short yearCon = (short)(buffer[ISO7816.OFFSET_CDATA+1] << 8 | (255 & buffer[ISO7816.OFFSET_CDATA]));
				short timeCon = (short)(buffer[ISO7816.OFFSET_CDATA+3] << 8 | (255 & buffer[ISO7816.OFFSET_CDATA+2]));
				byte mounthCon = buffer[ISO7816.OFFSET_CDATA+4];
				byte dayCon = buffer[ISO7816.OFFSET_CDATA+5];
				int resCon = control(yearCon,timeCon,mounthCon,dayCon);
				short retCon=0;
				if(resCon<0){
					switch(resCon){
						case -2: 
							retCon = SW_ERROR_BLOCKED;
							break;
						case -3:
							retCon = SW_ERROR_DEAD;
							break; 
						case -5:
							retCon = SW_ERROR_TIME_OVERFLOW;
							break;
						default:
							retCon =  SW_ERROR_DEFAULT;
						break;
					}
					ISOException.throwIt(retCon);
				}
				else{
					if(resCon != 0){
						buffer[0]= (byte)(resCon & 0xff);
						buffer[1]= (byte)((resCon >> 8 )&0xff);
						retCon = SW_ERROR_CONTROL_NEG;
						apdu.setOutgoingAndSend((short)  0, (  short)  2);
						ISOException.throwIt(retCon);
					}
				}
				break;
			case INS_VERIFICATION_CODE:
	
				break;
			default:
				ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

}
