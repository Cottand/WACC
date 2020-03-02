package ic.org.jvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import java.util.LinkedList

interface JvmMethod {
  fun body(): JvmAsm
}

interface JvmInstr {
  val code: String
  // override fun toString() = code
}

data class JvmLabel(val name: String) : JvmInstr {
  override val code = "$name:"
}

class JvmAsm private constructor(
  val instrs: PersistentList<JvmInstr>,
  private val methods: PersistentSet<JvmAsm> = persistentSetOf()
) {

  constructor(a: JvmAsm) : this(a.instrs, a.methods)

  constructor(init: JvmAsmBuilder.() -> Unit) : this(write(init))

  fun withMethod(m: JvmAsm) = JvmAsm(instrs, methods + m)
  fun withMethod(m: JvmMethod) = JvmAsm(instrs, methods + m.body())

  fun combine(other: JvmAsm) = JvmAsm(instrs + other.instrs, methods + other.methods)

  class JvmAsmBuilder {
    private val instructions = LinkedList<JvmAsm>()

    operator fun JvmInstr.unaryPlus() = instructions.addLast(instr(this))
    operator fun List<JvmInstr>.unaryPlus() = forEach { instructions.addLast(instr(it)) }
    operator fun JvmAsm.unaryPlus() = instructions.addLast(this)

    internal fun build() = instructions.fold(empty, JvmAsm::combine)

    fun withMethod(m: JvmMethod) = withMethod(m.body())
    fun withMethod(m: JvmAsm) = instructions.addLast(empty.withMethod(m))
  }

  companion object {
    fun instr(i: JvmInstr) = JvmAsm(persistentListOf(i))
    val empty = JvmAsm(persistentListOf())
    fun write(init: JvmAsmBuilder.() -> Unit) = JvmAsmBuilder().apply(init).build()
  }
}