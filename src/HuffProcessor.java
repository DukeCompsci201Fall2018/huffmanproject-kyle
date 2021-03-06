import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	//HuffProcessor hp = new HuffProcessor(HuffProcessor.DEBUG_HIGH);

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	private int[] readForCounts(BitInputStream in){
		int[] counts = new int[ALPH_SIZE+1];

		int word = in.readBits(BITS_PER_WORD);
		while(word != -1) {
			if(counts[word]<1){
				counts[word] = 1;
			}else{
				counts[word]++;
			}
			word = in.readBits(BITS_PER_WORD);
		}

		counts[PSEUDO_EOF] = 1;
		return counts;
	}

	private HuffNode makeTreeFromCounts(int[] counts){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>(HuffNode::compareTo);
		for(int i = 0; i < counts.length; i++){
			if(counts[i] > 0){
				pq.add(new HuffNode(i,counts[i],null,null));
			}
		}

		while(pq.size() > 1){
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1,left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}

		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[ALPH_SIZE + 1];
		 makePath(root,"",encodings);
		return encodings;
	}
	private void makePath(HuffNode root, String check, String[] encodings) {
		if(root == null){
			return;
		}
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = check;
			return;
		}

		makePath(root.myLeft, check+"0", encodings);
		makePath(root.myRight, check+"1", encodings);
	}


	private void writeHeader(HuffNode root, BitOutputStream out){
		if(root == null){
			return;
		}
		if(root.myLeft == null && root.myRight == null){
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
			return;
		}
		out.writeBits(1, 0);
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		String code = "";
		int check = in.readBits(BITS_PER_WORD);

		while(check != -1){
			code = codings[check];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			check = in.readBits(BITS_PER_WORD);
		}
		code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	 public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("Illegal header starts with " + bits);
		}
		if(bits == -1){
			throw new HuffException("Reading bits failed!");
		}

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	private HuffNode readTreeHeader(BitInputStream in){
		int bit = in.readBits(1);
		if(bit == -1){
			throw new HuffException("Reading bits failed!");
		}
		if(bit == 0){
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		else{
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value, 0, null, null);
		}

	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out){
		int bits;
		HuffNode current = root;
		while(true){
			bits = in.readBits(1);
			if(bits == -1){
				throw new HuffException("Reading bits failed!");
			}
			else{
				if(bits == 0){
					current = current.myLeft;
				}
				else{
					current = current.myRight;
				}
				if(current.myLeft == null && current.myRight == null){
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}

				}
			}


		}

	}



}