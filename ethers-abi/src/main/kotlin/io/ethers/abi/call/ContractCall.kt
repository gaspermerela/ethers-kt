package io.ethers.abi.call

import io.ethers.abi.error.ContractError
import io.ethers.abi.error.ContractRpcError
import io.ethers.abi.error.ExecutionRevertedError
import io.ethers.abi.error.RevertError
import io.ethers.core.FastHex
import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.CreateAccessList
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
import io.ethers.core.types.StateOverride
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcRequest
import io.ethers.signers.Signer
import java.math.BigInteger

/**
 * Contract call that can be used to both read and write data to the blockchain.
 * */
abstract class ReadWriteContractCall<C, S : PendingInclusion<*>, B : ReadWriteContractCall<C, S, B>>(
    provider: Middleware,
) : ReadContractCall<C, B>(provider) {
    /**
     * Try to sign the call using the [signer]. If [call] does not have all the required fields set, the function
     * returns null. The following fields must be set:
     * - [nonce]
     * - [gas]
     * - [gasPrice] or [gasFeeCap] + [gasTipCap]
     *
     * @return [TransactionSigned], or null if [call] is not ready to be signed (missing some fields).
     * */
    fun sign(signer: Signer): TransactionSigned? {
        val tx = call.toUnsignedTransactionOrNull() ?: return null
        return signer.signTransaction(tx)
    }

    /**
     * Create access list for **this** contract call on a given block [blockHash].
     * */
    fun createAccessList(blockHash: Hash): RpcRequest<CreateAccessList, RpcError> {
        return provider.createAccessList(call, blockHash)
    }

    /**
     * Create access list for **this** contract call on a given block [blockNumber].
     * */
    fun createAccessList(blockNumber: Long): RpcRequest<CreateAccessList, RpcError> {
        return provider.createAccessList(call, blockNumber)
    }

    /**
     * Create access list for **this** contract call on a given block [blockId].
     * */
    fun createAccessList(blockId: BlockId): RpcRequest<CreateAccessList, RpcError> {
        return provider.createAccessList(call, blockId)
    }

    /**
     * Sign the call using the [signer] and send it to the network. If call does not have all the required fields
     * set (see [sign] function), it will be filled using [Middleware.fillTransaction] before signing and sending.
     * */
    fun send(signer: Signer): RpcRequest<S, RpcError> {
        // if all params are set on "call", create signed tx directly
        val signed = sign(signer)
        if (signed != null) {
            return provider.sendRawTransaction(signed).map(::handleSendResult)
        }

        // create a new copy to avoid modifying the original
        val call = toCallRequest().from(signer.address)
        return provider.fillTransaction(call).andThen { unsigned ->
            val tx = signer.signTransaction(unsigned)
            provider.sendRawTransaction(tx).map(::handleSendResult).sendAwait()
        }
    }

    protected abstract fun handleSendResult(result: PendingTransaction): S
}

/**
 * Contract call that can be used only to read data from the blockchain. This corresponds to "pure" and "view"
 * functions in Solidity.
 * */
abstract class ReadContractCall<C, B : ReadContractCall<C, B>>(
    val provider: Middleware,
) : IntoCallRequest {
    protected val call = CallRequest().apply { chainId = provider.chainId }

    override fun toCallRequest(): CallRequest {
        // create a new instance to avoid modifying the original
        return CallRequest(call)
    }

    /**
     * Execute "eth_call" at the given [blockHash] and return the result of the call. This is a read-only call, and it
     * will not modify the blockchain state. Optionally, the [stateOverride] and [blockOverride] can be provided to
     * override the state on which the call is executed.
     * */
    @JvmOverloads
    fun call(
        blockHash: Hash,
        stateOverride: StateOverride? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<C, ContractError> {
        return call(BlockId.Hash(blockHash), stateOverride, blockOverride)
    }

    /**
     * Execute "eth_call" at the given [blockNumber] and return the result of the call. This is a read-only call, and it
     * will not modify the blockchain state. Optionally, the [stateOverride] and [blockOverride] can be provided to
     * override the state on which the call is executed.
     * */
    @JvmOverloads
    fun call(
        blockNumber: Long,
        stateOverride: StateOverride? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<C, ContractError> {
        return call(BlockId.Number(blockNumber), stateOverride, blockOverride)
    }

    /**
     * Execute "eth_call" at the given [BlockId] and return the result of the call. This is a read-only call, and it
     * will not modify the blockchain state. Optionally, the [stateOverride] and [blockOverride] can be provided to
     * override the state on which the call is executed.
     * */
    @JvmOverloads
    fun call(
        blockId: BlockId,
        stateOverride: StateOverride? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<C, ContractError> {
        return doCall(blockId, stateOverride, blockOverride)
    }

    /**
     * Implementation-specific logic for doing `eth_call`.
     * */
    protected abstract fun doCall(
        blockId: BlockId,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<C, ContractError>

    /**
     * Execute "debug_traceCall" at the given [blockHash] using the provided [TracerConfig], returning the result of
     * the tracer. Similar to [call] function, this is a read-only call, and it will not modify the blockchain state.
     * */
    fun <T> traceCall(blockHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        return traceCall(BlockId.Hash(blockHash), config)
    }

    /**
     * Execute "debug_traceCall" at the given [blockNumber] using the provided [TracerConfig], returning the result of
     * the tracer. Similar to [call] function, this is a read-only call, and it will not modify the blockchain state.
     * */
    fun <T> traceCall(blockNumber: Long, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        return traceCall(BlockId.Number(blockNumber), config)
    }

    /**
     * Execute "debug_traceCall" at the given [BlockId] using the provided [TracerConfig], returning the result of
     * the tracer. Similar to [call] function, this is a read-only call, and it will not modify the blockchain state.
     * */
    fun <T> traceCall(blockId: BlockId, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        return provider.traceCall(call, blockId, config)
    }

    protected fun tryDecodingContractRevert(err: RpcError): ContractError {
        val isRevertMessage = err.message.contains("execution revert", true)
        if (err.isExecutionError || isRevertMessage) {
            when {
                err.data == null && isRevertMessage -> return ExecutionRevertedError

                err.data != null && err.data!!.isTextual -> {
                    val data = err.data!!.textValue()

                    // if data is not a valid hex string, it's an already decoded revert error
                    if (!FastHex.isValidHex(data)) {
                        return RevertError(data)
                    }

                    // otherwise it could be a custom error
                    val contractError = ContractError.getOrNull(Bytes(data))
                    if (contractError != null) {
                        return contractError
                    }
                }
            }
        }

        return ContractRpcError(err)
    }

    protected abstract val self: B

    var from: Address?
        get() = call.from
        @JvmSynthetic set(value) {
            call.from = value
        }

    val to: Address?
        get() = call.to

    open val value: BigInteger?
        get() = call.value

    var gas: Long
        get() = call.gas
        @JvmSynthetic set(value) {
            call.gas = value
        }

    var gasPrice: BigInteger?
        get() = call.gasPrice
        @JvmSynthetic set(value) {
            call.gasPrice = value
        }

    var gasFeeCap: BigInteger?
        get() = call.gasFeeCap
        @JvmSynthetic set(value) {
            call.gasFeeCap = value
        }

    var gasTipCap: BigInteger?
        get() = call.gasTipCap
        @JvmSynthetic set(value) {
            call.gasTipCap = value
        }

    var nonce: Long
        get() = call.nonce
        @JvmSynthetic set(value) {
            call.nonce = value
        }

    val data: Bytes?
        get() = call.data

    var accessList: List<AccessList.Item>
        get() = call.accessList
        @JvmSynthetic set(value) {
            call.accessList = value
        }

    fun from(value: Address?): B {
        call.from = value
        return self
    }

    fun gas(value: Long): B {
        call.gas = value
        return self
    }

    fun gasPrice(value: BigInteger?): B {
        call.gasPrice = value
        return self
    }

    fun gasFeeCap(value: BigInteger?): B {
        call.gasFeeCap = value
        return self
    }

    fun gasTipCap(value: BigInteger?): B {
        call.gasTipCap = value
        return self
    }

    fun nonce(value: Long): B {
        call.nonce = value
        return self
    }

    fun accessList(value: List<AccessList.Item>): B {
        call.accessList = value
        return self
    }
}
