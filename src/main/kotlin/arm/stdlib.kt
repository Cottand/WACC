package ic.org.arm

import arrow.core.None
import arrow.core.some
import ast.Sizes
import ic.org.arm.addressing.ImmEqualLabel
import ic.org.arm.addressing.zeroOffsetAddr
import ic.org.arm.instr.ADDInstr
import ic.org.arm.instr.BInstr
import ic.org.arm.instr.BLInstr
import ic.org.arm.instr.CMPInstr
import ic.org.arm.instr.LDRInstr
import ic.org.arm.instr.MOVInstr
import ic.org.arm.instr.POPInstr
import ic.org.arm.instr.PUSHInstr
import ic.org.ast.CharT
import ic.org.ast.IntT
import ic.org.ast.Type
import ic.org.util.ARMAsm
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus

abstract class StdFunc {
  abstract val name: String
  abstract val body: ARMAsm
  internal val label by lazy { AsmLabel(name) }
}

abstract class IOFunc : StdFunc() {
  abstract val msgTemplate: String
  internal val msg by lazy { StringData(msgTemplate, msgTemplate.length - 1) }
  override val body by lazy { ARMAsm(instructions, msg.body) }
  abstract val instructions: PersistentList<ARMAsmInstr>
}

object PrintIntStdFunc : IOFunc() {
  override val name = "p_print_int"
  override val msgTemplate = "%d\\0"

  override val instructions by lazy {
    persistentListOf(
      label,
      PUSHInstr(LR),
      MOVInstr(rd = Reg(1), op2 = Reg(0)),
      LDRInstr(Reg.ret, ImmEqualLabel(msg.label)),
      ADDInstr(None, false, Reg(0), Reg(0), 4),
      BLInstr("printf"),
      LDRInstr(Reg.ret, 0),
      BLInstr("fflush"),
      POPInstr(PC)
    )
  }
}

object PrintLnStdFunc : IOFunc() {
  override val name = "p_print_ln"
  override val msgTemplate = "\\0"

  override val instructions by lazy {
    persistentListOf(
      label,
      PUSHInstr(LR),
      LDRInstr(Reg(0), ImmEqualLabel(msg.label)),
      ADDInstr(None, false, Reg(0), Reg(0), 4),
      BLInstr("puts"),
      LDRInstr(Reg(0), 0),
      BLInstr("fflush"),
      POPInstr(PC)
    )
  }
}

object PrintBoolStdFunc : IOFunc() {
  override val name = "p_print_bool"
  override val msgTemplate = "true\\0"

  private const val msg2Template = "false\\0"
  private val msg2 by lazy { StringData(msg2Template, msg2Template.length - 1) }

  override val instructions by lazy {
    persistentListOf(
      label,
      PUSHInstr(LR),
      CMPInstr(Reg(0), 0),
      LDRInstr(NECond, Reg(0), ImmEqualLabel(msg.label)),
      LDRInstr(EQCond, Reg(0), ImmEqualLabel(msg2.label)),
      ADDInstr(None, false, Reg(0), Reg(0), 4),
      BLInstr("printf"),
      LDRInstr(Reg.ret, 0),
      BLInstr("fflush"),
      POPInstr(PC)
    )
  }

  override val body by lazy { ARMAsm(instructions, msg.body + msg2.body) }
}

object PrintStringStdFunc : IOFunc() {
  override val name = "p_print_string"
  override val msgTemplate = "%.*s\\0"

  override val instructions by lazy {
    persistentListOf(
      label,
      PUSHInstr(LR),
      LDRInstr(Reg(1), Reg(0).zeroOffsetAddr),
      ADDInstr(None, false, Reg(2), Reg(0), 4),
      LDRInstr(Reg(0), ImmEqualLabel(msg.label)),
      ADDInstr(None, false, Reg(0), Reg(0), 4),
      BLInstr("printf"),
      LDRInstr(Reg.ret, 0),
      BLInstr("fflush"),
      POPInstr(PC)
    )
  }
}

object PrintReferenceStdFunc : IOFunc() {
  override val name = "p_print_reference"
  override val msgTemplate = "%p\\0"

  override val instructions by lazy {
    persistentListOf(
      label,
      PUSHInstr(LR),
      MOVInstr(None, false, Reg(1), Reg(0)),
      LDRInstr(Reg(0), ImmEqualLabel(msg.label)),
      ADDInstr(None, false, Reg(0), Reg(0), 4),
      BLInstr("printf"),
      LDRInstr(Reg.ret, 0),
      BLInstr("fflush"),
      POPInstr(PC)
    )
  }
}

/**
 * Returns the read value in r0, takes no arguments.
 */
sealed class ReadStdFunc : StdFunc() {
  abstract val type: Type
  abstract val template: StringData

  override val body by lazy {
    ARMAsm.write {
      data { +template }

      +label
      +PUSHInstr(LR)
      +ADDInstr(rd = SP, s = false, rn = SP, int8b = -type.size.bytes)
      +MOVInstr(rd = Reg.sndArg, op2 = SP) // Place Stack - 4 addr in r1
      +LDRInstr(Reg.fstArg, ImmEqualLabel(template.label))
      +ADDInstr(s = false, rd = Reg.fstArg, rn = Reg.fstArg, int8b = Sizes.Word.bytes)
      +BLInstr("scanf")
      +type.sizedLDR(Reg.ret, SP.zeroOffsetAddr)
      +ADDInstr(rd = SP, s = false, rn = SP, int8b = type.size.bytes)
      +POPInstr(PC)
    }
  }
}

object ReadInt : ReadStdFunc() {
  override val name = "p_read_int"
  override val type: Type = IntT
  private const val templateStr = "%d\\0"
  override val template = StringData(templateStr, templateStr.length - 1)
}

object ReadChar : ReadStdFunc() {
  override val name = "p_read_char"
  override val type = CharT
  private const val templateStr = " %c\\0"
  override val template = StringData(templateStr, templateStr.length - 1)
}

object MallocStdFunc : StdFunc() {
  override val name = "malloc"
  override val body = ARMAsm.empty
}

/**
 * C stdlib function. NOT FOR USE IN GENERATED CODE. Use [FreeFunc] instead, for example.
 */
object FreeStdLibFunc : StdFunc() {
  override val name = "free"
  override val body = ARMAsm.empty
}

object FreeFunc : StdFunc() {
  override val name = "p_free"
  private const val errormsg = "NullReferenceError: dereference a null reference\\n\\0"
  private val msg0 by lazy { StringData(errormsg, errormsg.length - 2) }
  override val body = ARMAsm.write {
    data { +msg0 }
    +label
    +PUSHInstr(LR)
    +CMPInstr(None, Reg(0), 0)
    +LDRInstr(EQCond, Reg(0), ImmEqualLabel(msg0.label))
    +BInstr(EQCond.some(), RuntimeError.label)
    +BLInstr(FreeStdLibFunc.label)
    +POPInstr(PC)

    withFunction(RuntimeError)
  }
}

object StrcmpStdFunc : StdFunc() {
  override val name = "strcmp"
  override val body = ARMAsm.empty
}
