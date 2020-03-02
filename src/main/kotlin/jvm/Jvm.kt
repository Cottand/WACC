package ic.org.jvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import java.util.LinkedList

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

  constructor(init: BuilderScope.() -> Unit) : this(write(init))

  fun withMethod(m: JvmMethod): JvmAsm = JvmAsm(instrs, methods + m.asm)
  fun withMethods(ms: List<JvmMethod>) = JvmAsm(instrs, methods + ms.map { it.asm })

  fun combine(other: JvmAsm) = JvmAsm(instrs + other.instrs, methods + other.methods)

  class BuilderScope {
    private val instructions = LinkedList<JvmAsm>()

    operator fun JvmInstr.unaryPlus() = instructions.addLast(instr(this))
    operator fun List<JvmInstr>.unaryPlus() = forEach { instructions.addLast(instr(it)) }
    operator fun JvmAsm.unaryPlus() = instructions.addLast(this)

    internal fun build() = instructions.fold(empty, JvmAsm::combine)

    fun withMethod(m: JvmMethod) = instructions.addLast(empty.withMethod(m))
    fun withMethods(ms: List<JvmMethod>) = instructions.addLast(empty.withMethods(ms))
  }

  companion object {
    fun instr(i: JvmInstr) = JvmAsm(persistentListOf(i))
    val empty = JvmAsm(persistentListOf())
    fun write(init: BuilderScope.() -> Unit) = BuilderScope().apply(init).build()
  }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class JvmGenOnly