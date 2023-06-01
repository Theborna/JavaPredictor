package hardwar.branch.prediction.judged.SAg;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAg implements BranchPredictor {
    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PHT; // page history table

    public SAg() {
        this(4, 2, 8, 4);
    }

    public SAg(int BHRSize, int SCSize, int branchInstructionSize, int KSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;

        // Initialize the PABHR with the given bhr and Ksize
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initialize the PHT with a size of 2^size and each entry having a saturating
        // counter of size "SCSize"
        PHT = new PageHistoryTable(1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction instruction) {
        Bit[] address = getRBAddressLine(instruction.getInstructionAddress());
        ShiftRegister BHR = PSBHR.read(address);
        // read the bhr
        Bit[] bhrData = BHR.read();
        // initialize the PHT if empty
        PHT.putIfAbsent(bhrData, getDefaultBlock());
        // read from the cache
        Bit[] block = PHT.get(bhrData);
        // load into the SC register
        SC.load(block);
        PSBHR.write(address, BHR.read());
        return BranchResult.of(block[0].getValue());
    }

    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        Bit[] address = getRBAddressLine(branchInstruction.getInstructionAddress());
        ShiftRegister BHR = PSBHR.read(address);
        // counting from the SC register
        Bit[] counted = CombinationalLogic.count(this.SC.read(),
                BranchResult.isTaken(actual), CountMode.SATURATING);
        // updating our cache
        PHT.put(BHR.read(), counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PSBHR.write(address, BHR.read());
    }

    private Bit[] getRBAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return hash(branchAddress);
    }

    /**
     * hash N bits to a K bit value
     *
     * @param bits program counter
     * @return hash value of fist M bits of `bits` in K bits
     */
    private Bit[] hash(Bit[] bits) {
        Bit[] hash = new Bit[KSize];

        // XOR the first M bits of the PC to produce the hash
        for (int i = 0; i < branchInstructionSize; i++) {
            int j = i % KSize;
            if (hash[j] == null) {
                hash[j] = bits[i];
            } else {
                Bit xorProduce = hash[j].getValue() ^ bits[i].getValue() ? Bit.ONE : Bit.ZERO;
                hash[j] = xorProduce;

            }
        }
        return hash;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return null;
    }
}
