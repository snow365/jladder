package org.jladder.adapter.protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jladder.adapter.protocol.enumtype.JladderForwardWorkerStatusEnum;
import org.jladder.adapter.protocol.listener.JladderOnConnectedListener;
import org.jladder.adapter.protocol.listener.JladderOnReceiveDataListener;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class JladderForwardWorker extends SimpleChannelInboundHandler<JladderMessage> {
	
	private volatile JladderForwardWorkerStatusEnum status = JladderForwardWorkerStatusEnum.Terminated;
	private EventLoopGroup eventLoopGroup;
	private Channel channel;
	private String remoteHost;
	private int remotePort;
	private Map<Long, JladderOnReceiveDataListener> listenerMap = new ConcurrentHashMap<>();
	
	public JladderForwardWorker(String proxyHost, int proxyPort) {
		this(proxyHost, proxyPort, new NioEventLoopGroup());
	}
	
	public JladderForwardWorker(String remoteHost, int remotePort, EventLoopGroup eventLoopGroup) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.eventLoopGroup = eventLoopGroup;
	}

	public JladderOnConnectedListener connect() {
		if (!isCanBeStart()) {
			throw new IllegalStateException("worker cann't be connect, current_status=" + status);
		}
		status = JladderForwardWorkerStatusEnum.Starting;
		
		// init bootstrap
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.group(eventLoopGroup);
		bootstrap.handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ch.pipeline().addLast(childHandler());
			}
		});	
		ChannelFuture chanelFuture = bootstrap.connect(remoteHost, remotePort);
		this.channel = chanelFuture.channel();
		chanelFuture.addListener(f -> {
			if (f.isSuccess()) {
				status = JladderForwardWorkerStatusEnum.Running;
			}
		});
		return new JladderOnConnectedListener();
	}
	
	public ChannelHandler[] childHandler() {
		return new ChannelHandler[] { this };
	}
	
	private boolean isCanBeStart() {
		return status != JladderForwardWorkerStatusEnum.Running && status != JladderForwardWorkerStatusEnum.Starting;
	}

	public JladderOnReceiveDataListener writeAndFlush(JladderMessage message) {
		if (status != JladderForwardWorkerStatusEnum.Running) {
			throw new IllegalStateException("channel not connect or has closed.");
		}

		listenerMap.put(message.getId(), new JladderOnReceiveDataListener());
		
		this.channel.writeAndFlush(message).addListener(f -> {
			// TODO
			// sign writable
		});
		
		return listenerMap.get(message.getId());
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, JladderMessage msg) throws Exception {
		listenerMap.get(msg.getId()).fireReadEvent(new JladderByteBuf(msg.getBody()));
        ctx.fireChannelRead(msg);
	}
}