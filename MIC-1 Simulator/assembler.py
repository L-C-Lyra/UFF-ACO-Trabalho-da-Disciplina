# assembler.py

class Assembler:
    OPCODE_MAP = {
        'LODD': ('0000', True), 'STOD': ('0001', True), 'ADDD': ('0010', True),
        'SUBD': ('0011', True), 'JPOS': ('0100', True), 'JZER': ('0101', True),
        'JUMP': ('0110', True), 'LOCO': ('0111', True), 'LODL': ('1000', True),
        'STOL': ('1001', True), 'ADDL': ('1010', True), 'SUBL': ('1011', True),
        'JNEG': ('1100', True), 'JNZE': ('1101', True), 'CALL': ('1110', True),
        'PSHI': ('1111000000000000', False), 'POPI': ('1111001000000000', False),
        'PUSH': ('1111010000000000', False), 'POP':  ('1111011000000000', False),
        'RETN': ('1111100000000000', False), 'SWAP': ('1111101000000000', False),
        'INSP': ('11111100', True), 'DESP': ('11111110', True),
        'HALT': ('0110', True),
    }

    def assemble(self, source_code):
        machine_code = []
        assembly_lines = []
        
        raw_lines = source_code.split('\n')
        
        valid_lines = [line.strip().upper() for line in raw_lines if line.strip() and not line.strip().startswith(';')]

        for line_num, line in enumerate(valid_lines):
            parts = line.split()
            mnemonic = parts[0]
            
            if mnemonic not in self.OPCODE_MAP:
                raise ValueError(f"Instrução desconhecida na linha {line_num+1}: {mnemonic}")

            opcode_info, has_operand = self.OPCODE_MAP[mnemonic]
            
            if has_operand:
                if mnemonic == 'HALT':
                    operand_val = line_num
                elif len(parts) < 2:
                     raise ValueError(f"A instrução {mnemonic} requer um operando na linha {line_num+1}.")
                else:
                    try:
                        operand_val = int(parts[1])
                    except ValueError:
                        raise ValueError(f"Operando inválido na linha {line_num+1}: {parts[1]}")
                
                if mnemonic in ['INSP', 'DESP']:
                    if not (0 <= operand_val <= 255): raise ValueError(f"Operando para {mnemonic} deve estar entre 0 e 255.")
                    operand_bin = format(operand_val, '08b')
                else:
                    if not (0 <= operand_val <= 4095): raise ValueError(f"Endereço para {mnemonic} deve estar entre 0 e 4095.")
                    operand_bin = format(operand_val, '012b')
                
                binary_instruction = opcode_info + operand_bin
            else:
                binary_instruction = opcode_info

            machine_code.append(int(binary_instruction, 2))
            assembly_lines.append(line)
            
        return machine_code, assembly_lines
